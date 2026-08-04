# 주변 코스 조회 & CourseReadModel 현황 분석

> 워크스트림 1 (ReadModel 재설계 + Spring Cache) 착수 문서. 2026-08-03 코드 기준.
> 목표: **리드모델을 실제 조회 경로로 활성화 + 기존 경로 Deprecated + Spring Cache(Redis) 도입 + 엔티티 재설계.**
> 제약: 외부 API 응답 스펙 불변 ([../../core/05-api.md](../../core/05-api.md)).

## 1. 대상 API

```
GET /v1/courses?lat&lng&radiusM(≤20000, 기본 2000)&sort(DISTANCE|POPULARITY)&ownerUuid&minDistanceM&maxDistanceM&minElevationM&maxElevationM
→ List<CourseMapResponse>
```

- 서비스 첫 화면(지도)에서 가장 빈번히 호출되는 핵심 API.
- 응답 `CourseMapResponse`: 코스 정보(이름/소유자/source/시작좌표/routeUrl/checkpointsUrl/thumbnailUrl/거리/고도 3종/createdAt) + **내 고스트**(myGhostInfo) + **TOP4 러너 프로필**(uuid/사진/기록) + 러너 수.

## 2. 개선 히스토리 (PR)

### [PR #101](https://github.com/SGMRT/sgmrt-server/pull/101) — 쿼리 옵티마이징 (2025-10)
- 문제: 1000 VU 부하 테스트에서 300 VU부터 타임아웃. 원인은 `1 + 2*N` 쿼리(코스 10개당 TOP4 러너·러너 수 개별 조회) + 하버사인 FileSort로 **DB CPU 99%**.
- 개선: 쿼리 3회로 축소(반경 내 코스 → (course_id, member_id) GROUP BY로 멤버별 최고기록 집계 → 상세 IN 조회), 하버사인 정렬을 앱 서버로 이전.
- CTE 단일 쿼리(980ms, Member가 드라이빙 테이블로 선정되는 문제) 대신 **쿼리 분할 + 루즈 인덱스 스캔 + 커버링 인덱스**(160ms) 채택:
  - `idx_record_course (is_public, deleted, course_id, member_id, duration_sec)`
  - `idx_coordinates_course (is_public, deleted, start_latitude, start_longtitude)`
- 결과: 1000 VU에서 에러율 100% → 4~5%. 결론에서 CQRS 리드모델 필요성 제기.

### [PR #148](https://github.com/SGMRT/sgmrt-server/pull/148) — 리드모델 엔티티 도입 (2026-01)
- `CourseReadModel` 엔티티 신설: 코스 요약 + TOP4 러너 + runnersCount 역정규화.
- 동기화: `RunFinishedEvent`를 **BEFORE_COMMIT** 리스너로 수신 → `findByCourseIdForUpdate`(비관락 X락, course_id UK로 레코드락 한정) → `insertIfBetter()` 증분 갱신. 러닝 저장과 원자적.
- 결과(부하 테스트): 에러율 ~0%, P90 5s → 1.8~1.9s, DB CPU 98% → 27%. SPOF는 앱 서버 CPU로 이동(스케일아웃 여지).
- **단, 조회 경로 전환은 이 PR에 포함되지 않아 리드모델은 현재 쓰기만 되고 읽히지 않음.**

## 3. 현재 구조: 조회 경로가 2개 존재

### 3-1. 활성 경로 — `CourseFacade.findCoursesByPositionCached()` (수동 Redis 캐시)

`CourseApi.getCoursesByPosition()`이 현재 호출하는 경로. (리드모델 경로 호출은 `CourseApi.java:40`에 **주석 처리**되어 있음)

```
1. courseService.findNearbyCourses()                  # 매 요청 DB 조회 (캐시 안 됨!)
   └ CustomCourseRepositoryImpl.findCoursesWithFilters (QueryDSL)
     - Course ⋈ Running(러닝수 집계) ⋈ Member(fetch join) ⋈ CourseSubscription(viewer 구독)
     - 바운딩박스 + 필터(거리/고도/ownerUuid) + (공개 OR 내가 구독한 비공개)
     - DISTANCE: 하버사인 정렬(DB) / POPULARITY: 러닝 수 정렬
2. courseCacheRepository.findAllById(전체 코스 ID)     # Redis MGET, 키 course:{0}:{id}, TTL 60분
3. limitCoursesForViewer(…, 10)                       # 본인(≤5)/추천(≤2)/타인/더미 순 랜덤 선별
4. 분기:
   - 캐시 미스 존재 → 미스 코스만 TOP4 러너·러너수 DB 조회(runningQueryService) → Redis MSET 저장
   - 전체 히트 → 캐시 값 사용
5. 내 최고기록(내 고스트)은 캐싱하지 않고 매번 DB 조회
6. CourseMapper로 응답 조립
```

**특징/문제**:
- "캐시"가 절약하는 것은 TOP4·러너수 쿼리뿐. **1번의 무거운 반경 검색(QueryDSL, 하버사인·집계·4-way 조인)은 매 요청 DB 실행** — PR #101에서 병목으로 지목한 구조가 여전히 앞단에 남아 있음.
- 캐시 히트 분기(`handleCourseCacheHit`)는 반경 내 **전체** 코스 ID로 내 최고기록을 조회 (선별된 10개만 필요한데 전부 조회).
- 미스 여부 판정은 반경 내 전체 ID 기준, 실제 조회·저장은 선별된 10개 기준 — 판정과 처리 범위 불일치.
- 캐시 무효화: `CourseCacheEventListener`(AFTER_COMMIT)가 RunFinished/RunUpdated 시 `deleteById(courseId)`. 코스명 변경/삭제/공개전환에 대한 무효화는 없음.
- 캐시 키에 해시태그가 `{0}` 하드코딩 (클러스터 슬롯 분산 실험 흔적, 현재는 전 키 동일 슬롯).
- 캐시 로직(히트/미스 분기, 저장)이 파사드 비즈니스 로직과 뒤섞여 있음 → **Spring Cache 추상화 도입 대상**.

### 3-2. 비활성 경로 — `CourseFacade.findCoursesByPosition()` (리드모델)

```
1. getBoundingBoxLatLngs()로 범위 계산
2. readModelRepository.findCoursesForMap(범위, 50)    # 네이티브 쿼리 1방
   - course_read_model ⋈ member×4 (LEFT JOIN, TOP4 프로필)
   - is_public=true + 바운딩박스, ORDER BY start_lat/lng, LIMIT 50
3. limitCoursesForViewer(…, limit)                    # 랜덤 선별 (기존 로직 재사용)
4. CourseMapDto.toResponse(null)                      # 응답 변환
```

**활성 경로 대비 미구현 갭 (전환 블로커)**:

| 갭 | 상세 |
|---|---|
| 내 고스트 없음 | `toResponse(null)` — myGhostInfo 항상 null |
| 응답 필드 다수 null | checkpointsUrl, thumbnailUrl, distance, elevation 3종, createdAt — 리드모델에 없는 필드 (**API 불변 위반**) |
| 필터/정렬 미지원 | sort(DISTANCE/POPULARITY), ownerUuid, min/max 거리·고도 필터 전부 무시. 정렬이 `ORDER BY start_lat, start_lng`(무의미) |
| 구독 비공개 코스 미포함 | 활성 경로는 "공개 OR 내가 구독한 비공개"를 조회하지만 리드모델은 is_public=true만 |
| **백필 없음** | 리드모델은 코스 "공개 전환" 시점에만 생성 → PR #148 배포 이전에 공개된 기존 코스들은 리드모델 행이 없어 지도에서 사라짐 |

## 4. 쓰기(동기화) 경로 현황

| 시점 | 위치 | 동작 |
|---|---|---|
| 러닝 종료 | `ReadModelSyncListener` (BEFORE_COMMIT) | `findByCourseIdForUpdate`(X락) → `insertIfBetter` TOP4 증분 갱신 → `countDistinctRunnersByCourseId` 재집계 → save. **리드모델이 없으면 스킵**(warn 로그) |
| 코스 공개 전환 | `CourseService.syncReadModelPublicity` | 리드모델 조회-or-생성(`createReadModelForCourse`: 소유자 최고기록으로 TOP1 초기화 + runnersCount 집계) → `makePublic()` |
| 코스 비공개 전환 | 〃 | `makePrivate()` (행 유지) |
| 코스 삭제 | `CourseService.deleteCourse` | 리드모델 **hard delete** (Course 자체는 `@SoftDelete`) |
| 코스명 변경 | `CourseService.updateCourseName` | **리드모델 동기화 없음** → name 불일치 발생 |
| 러닝 삭제/비공개 전환 | — | **동기화 없음** — TOP4에 있던 기록이 삭제돼도 리드모델에 잔존 (`containsMember()`가 재계산 판단용으로 존재하나 호출처 없음) |
| 회원 탈퇴 | — | 조회 쿼리에서 `deleted_at IS NULL` LEFT JOIN으로 프로필만 null 처리. top{n}_member_id는 잔존, 순위 밀림 없음 |

## 5. `CourseReadModel` 엔티티 현황 (재설계 대상)

- 테이블 `course_read_model`, 인덱스: `uk_course_id`(unique), `idx_is_public_location (is_public, start_lat, start_lng)`
- 필드: courseId(FK 아님) / name / ownerUuid / routeUrl / startLat·startLng / **top1~4MemberId + top1~4TimeSeconds (8컬럼)** / runnersCount / isPublic / source
- `insertIfBetter()` + `updateExistingRunner()` + `shiftDown()` + `swap()` — 4위까지의 순위 삽입/교체를 **필드 수동 시프트**로 구현 (379줄 중 약 200줄이 이 로직, 자리 수 변경 시 전면 수정 필요)
- 응답에 필요한 코스 필드(거리·고도·thumbnailUrl·checkpointsUrl·createdAt)가 없음 → 3-2 갭의 원인
- `create()` 시 member가 null이면 ownerUuid에 null 주입 → `nullable=false` 제약과 충돌 소지 (OFFICIAL/더미 코스)

## 6. 관련 도메인 결합 메모

- 활성 경로: `CourseFacade` → `RunningQueryService`(TOP4·러너수·내 기록), `MemberService`(viewer 조회) — course→running·member 직접 의존
- 쓰기 경로: `ReadModelSyncListener`·`CourseService` → `RunningRepository` 직접 사용 (course→running.infra)
- `CustomCourseRepositoryImpl.findCourseIdsWithFilters`는 @Deprecated 데드코드

## 7. 리팩토링 방향 (사용자 결정 반영)

1. **리드모델 경로를 실제 조회 경로로 활성화** — 위 3-2의 갭(응답 필드, 내 고스트, 필터/정렬, 구독 코스, 백필)을 해소한 뒤 전환
2. **기존 경로(`findCoursesByPositionCached` + `CourseCacheRepository` 수동 캐시) Deprecated** 처리 후 제거 수순
3. **Spring Cache 추상화 도입** — `RedisCacheManager` + `@Cacheable`/`@CacheEvict`로 리드모델 조회 결과를 Redis 캐싱, 수동 히트/미스 분기 제거
4. **엔티티 재설계** — TOP4 8컬럼 구조, 응답 필드 부족, 동기화 누락(코스명/러닝 삭제) 해소

## 8. 재설계 시 결정 필요 사항 (Open Questions)

- [ ] **TOP4 구조**: 8컬럼 유지+시프트 로직 정리 vs 별도 랭킹 테이블(1:N) 정규화 vs JSON 컬럼 — 조회 1방 유지 여부와 트레이드오프
- [ ] **응답 필드 충족 방법**: 리드모델에 코스 필드(거리/고도/썸네일 등) 추가 역정규화 vs Course 테이블 JOIN 병행
- [ ] **내 고스트 조회**: 캐시 불가(개인화) — 리드모델 경로에서도 별도 쿼리 유지? 범위(선별 10개만)?
- [ ] **필터/정렬**: sort=DISTANCE(하버사인)·POPULARITY, 거리/고도 필터를 리드모델 쿼리로 흡수 (필드 추가 필요) — 실제 클라 사용 여부 확인 후 범위 결정
- [ ] **구독 비공개 코스**: 리드모델 경로에 포함할지 (포함 시 is_public 필터로는 불가 — 구독 조인 필요)
- [ ] **백필 전략**: 기존 공개 코스 전체에 리드모델 생성 (일회성 마이그레이션 vs 조회 시 lazy 생성)
- [ ] **캐시 단위/키**: 코스별(`course:{id}`) vs 바운딩박스/지오해시 단위 캐싱 — 랜덤 선별(매 요청 다른 10개)과 캐시 적중률의 관계 정리 필요
- [ ] **무효화 이벤트 정비**: RunFinished/RunUpdated 외에 코스명 변경·삭제·공개전환·러닝 삭제 시 캐시/리드모델 동기화
- [ ] **랜덤 선별 로직 위치**: `limitCoursesForViewer`(본인/추천/타인/더미 비율)가 파사드에 있음 — 유지/분리
- [ ] **러닝 삭제·기록 악화 시 TOP4 재계산 정책**: 증분 불가 케이스의 전체 재계산 트리거
