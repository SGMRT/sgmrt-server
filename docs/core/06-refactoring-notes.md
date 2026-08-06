# 06. 리팩토링 계획

2026-08 코드 전수 분석 + 개발자 결정을 바탕으로 한 리팩토링 로드맵.
공통 제약: **외부 API 절대 불변** ([05-api.md](05-api.md)).

## 우선순위 요약

| 순서 | 워크스트림 | 이유 |
|---|---|---|
| 0 | 선행 조치 (버그/보안) | 리팩토링과 무관하게 운영 리스크. 작고 독립적이라 먼저 끝낼 수 있음 |
| 1 | **ReadModel 재설계 + Spring Cache** | 현재 착수 주제. course 도메인에 국한되어 범위가 명확하고, 캐시 전략 재정립이 이후 조회 로직 전반의 기준이 됨 |
| 2 | **Pacemaker 로직 재설계** | 방향 확정됨(CB/워커 제거). 삭제 위주라 리스크 낮고, 끝나면 pacemaker→running 결합도 함께 정리됨 |
| 3 | **전반 코드 리팩토링 (도메인 강결합 해제)** | 1·2가 끝나면 남는 결합이 명확해짐. 범위가 넓어 세부 주제로 쪼개 진행 |
| 4 | **인프라 재구성 (EB → K8s)** | 코드 리팩토링과 독립 트랙. 코드가 안정된 뒤 전환하는 게 안전. RDS는 유지 |

---

## 0. 선행 조치 (버그/보안 — 리팩토링 전 처리)

- [x] **JWT 만료 단위 불일치** — 설정값(ms)을 코드가 초 단위로 해석하던 문제 수정. `JwtTokenFactory`는 `ChronoUnit.MILLIS`, `RefreshTokenService`는 `TimeUnit.MILLISECONDS`로 통일. **배포 시 액세스 토큰 수명이 ~7일 → 10분(dev 설정 기준)으로 줄어들므로 클라이언트 reissue 동작 확인 필요**
- [x] `JwtAuthFilter.SIGN_ENDPOINTS` 선행 슬래시 누락 수정 (`"v1/auth/firebase-signup"` → `"/v1/auth/firebase-signup"`)
- [x] `ErrorCode` `P-002` 중복 해소 — `VERSION_NOT_REQUIRED_FOR_BROADCAST`를 `P-004`로 변경 (어드민 전용 API에서만 사용되어 앱 클라 영향 없음)
- [x] ~~dev SQS 큐 이름 뒤바뀜~~ → **확인 결과 설정 오타 아님.** 실제 AWS 큐 이름 자체가 `...-dlq`(메인 역할)/`...-main`(DLQ 역할)으로 생성되어 있어 설정은 AWS 상태와 일치. 4번(인프라 재구성) 때 큐 이름 정정 권장
- [ ] **시크릿 평문 커밋 해소** (보류) — `application-local.yml`/`application-dev.yml`의 AWS 키, RDS 비밀번호, OpenAI 키, JWT 시크릿, Discord 웹훅. 값 로테이션 + 환경변수/Parameter Store 외부화 + git 히스토리 정리. EB/CodeBuild 환경변수 세팅이 선행돼야 하므로 인프라 재구성(4번)과 연계해 별도 진행
- [ ] `SecurityConfig` 블랙리스트 → 화이트리스트 전환 (보류) — 블랙리스트에 없는 deprecated 경로(`/v1/member/{uuid}/push-token` 단수형) 등이 인증 필수로 바뀌면 구버전 클라 파손 위험. 클라 호출 패턴 분석 후 별도 PR

---

## 1. ReadModel 엔티티 재설계 + READ/WRITE 로직 재설정 ⬅ 현재 주제

**목표**: `CourseReadModel` 재설계, 읽기/쓰기 경로 재정립, Redis 기반 **Spring Cache**(`@Cacheable` 등) 도입.

> **진행 상황 (2026-08-06)**: 상세 설계·구현은 `docs/refactoring/course-read-model/` 참조. PR-1(쓰기 측: RankSlot/TopRunners VO, 엔티티 재설계, CourseReadModelWriter 직접 호출, ReadModelSyncListener 삭제) 완료. 조회 전환·캐시는 셀 버킷 전환(#168)으로 마무리 — 설계는 `docs/design/course-cell-bucket-cache-design.md`, 캐시키 선택 근거는 `docs/refactoring/course-read-model/cache/07·08`.
>
> **결론이 뒤집힌 항목**: 목표에 적힌 "Spring Cache(`@Cacheable`) 도입"은 채택하지 않았다. 지도 조회는 반경을 덮는 셀 여러 개를 읽고 **미스인 셀만 채우는 부분 히트**가 이득의 실체인데, 결과셋 하나를 통째로 캐싱하는 `@Cacheable` 추상화로는 그 동작을 표현할 수 없다. `RedisTemplate` 기반 어댑터(`CourseCellCache`)로 직접 다루기로 하고, `@Cacheable` 사용처가 0이 되면서 `CacheConfig`·`@EnableCaching`도 제거했다.

### 현재 상태 (문제점)
- `CourseReadModel`(379줄, main 최대 파일): courseId(unique) + **top1~top4 멤버ID/기록 8컬럼 역정규화** + runnersCount. `insertIfBetter()`/`shiftDown()` 수동 배열 시프트 로직 내장
- 쓰기: `ReadModelSyncListener`가 `CourseRunEvent`를 **BEFORE_COMMIT**으로 수신 → `findByCourseIdForUpdate`(X락) → 증분 갱신. 러닝 생성 트랜잭션이 그만큼 길어지고 락 경합 지점
- 읽기: `CourseFacade`(357줄)가 Redis 캐시(`course:{id}`, TTL 60분, MSET/MGET)를 **수동으로** 히트/미스 분기 — 캐시 로직과 비즈니스 로직 혼재
- 캐시 무효화: `CourseCacheEventListener`(AFTER_COMMIT)가 RunFinished/RunUpdated 시 수동 삭제 — 이 리스너와 두 이벤트는 #168에서 구경로와 함께 제거됐다(아래 참조)
- 코스 삭제/공개전환 시 읽기모델·구독 동기화가 `CourseService`에 절차적으로 흩어져 있음

### 검토 과제
- [ ] TOP4 8컬럼 → 정규화(별도 랭킹 테이블) vs 유지 결정. 랭킹 조회 패턴(`/top-ranking`, `/ranking`, `/top-percentage`)과 함께 재설계
- [ ] 쓰기 경로: BEFORE_COMMIT 동기 갱신 유지 vs AFTER_COMMIT/비동기 전환(정합성 요구 수준 결정)
- [x] 읽기 경로: `CourseFacade`의 수동 캐시 분기 제거 → 캐시 판정·부분 채움을 `CourseReadModelReader`로 이관 (#168). Spring Cache 추상화 대신 `CourseCellCache` 어댑터 채택 — 위 "결론이 뒤집힌 항목" 참조
- [x] 캐시 키/TTL 전략, 무효화 경로 정리 (#168) — 키는 코스 시작점의 geohash p6 셀, TTL 600초. 무효화는 **이벤트가 아니라 직접 호출**로 정리했다: 리드모델을 바꾸는 쓰기 경로(러닝 완주·기록 수정·기록 삭제, 코스 수정·삭제·공개전환)가 `CourseMapCacheEvictor`를 직접 불러 셀 1개 DEL을 예약하고, 실행만 `TransactionSynchronizationManager`로 커밋 후에 일어난다. `RunFinishedEvent`/`RunUpdatedEvent`는 구경로 캐시 리스너가 유일한 소비자였고, 구경로 제거로 소비자가 0이 되어 **함께 삭제**했다. `RunningCommandService`에 남은 이벤트는 푸시(`PushEventListener`)가 소비하는 `CourseRunEvent` 하나뿐이다
- [x] `CourseFacade` 책임 분리 (#168) — 캐시는 Reader, Facade에는 랜덤 선별과 DTO 조립만 남음. 구경로(`findCoursesByPositionCached`)는 `@Deprecated` 존치로 계획했으나, **프로덕션 호출자가 0인 죽은 코드로 확인돼 같은 PR에서 제거**했다(`CourseCacheRepository`·`CourseCacheEventListener`·`CourseQueryModel`·`CourseSubMapper` 동반 제거). 근거는 `docs/design/course-cell-bucket-cache-design.md` D13
- [ ] 관련 데드코드 정리: Redisson 분산락, `RedisRateLimiterRepository`

---

## 2. Pacemaker 로직 재설계

**목표**: 서킷브레이커·복구 워커 제거, 트랜잭션 분리(TX1 생성 / TX2 상태전이 / LLM 호출은 TX 밖) 중심으로 단순 재구성.

- [ ] Resilience4j 서킷브레이커 제거 (현재도 무동작 — `getState()` 조회만 하고 호출을 안 감쌈)
- [ ] `PacemakerRecoveryWorker` + `PacemakerRecoveryService` + `PacemakerRecoveryPrepareService` 제거 — readOnly TX 안 LLM 호출 문제도 자연 해소
- [ ] ShedLock 의존성/테이블 제거 여부 결정 (워커 제거 후 사용처 없음)
- [ ] `@Async` 이중 체인 해소 — `PacemakerLlmTriggerService` → `PacemakerLlmService`가 같은 `llmTaskExecutor`를 2번 타는 구조(요청당 스레드 2개, 풀 고갈 위험)를 단일 비동기 진입점으로
- [ ] 서비스 12개 과분해 재구성 — 워커/CB 제거 후 남는 책임 기준으로 통합
- [ ] 상태 명칭 정리: enum `FAILED` vs 코드/로그의 "FALLBACK" 통일
- [ ] `PacemakerOpenAiRestClient` 자체 재시도 루프(`Thread.sleep`) 정리 — 유지/제거/Spring Retry 일원화 결정
- [ ] 워커 제거에 따른 미완료(INIT/PROCEEDING 고아) 건 처리 정책 결정 — 클라이언트 폴링 타임아웃과 함께
- [ ] pacemaker→running 결합 정리(3번 워크스트림과 겹침): 레이트리밋 저장소가 `running.infra.redis`에 있는 문제, `RunningNotFoundException` 오용

---

## 3. 전반 코드 리팩토링 (도메인 강결합 해제)

**목표**: 도메인 간 직접 의존 제거, 일관성 회복.

### 도메인 결합 해제
- [ ] `member` → `pacemaker.VdotService` (`MemberVdotWriter`·`MemberService`가 주입) — VDOT의 소속 도메인 결정 후 의존 방향 정리. 이 계산을 수행하던 `RunFinishedEventListener`는 #165에서 이미 사라졌고(`MemberVdotWriter` 직접 호출로 전환), **의존 방향 문제 자체는 그대로 남아 있다**
- [ ] `pacemaker` → `running.infra.redis.RedisRunningRepository` — 레이트리밋 저장소를 pacemaker 소유로 이동 (2번과 연계)
- [ ] `pacemaker`가 `running` 예외를 던지는 문제 — 자기 도메인 예외로
- [ ] `Member.toStringForPacemakerPrompt()` — 엔티티의 LLM 프롬프트 생성을 pacemaker 쪽 프롬프트 생성기로 이동
- [ ] `Running.calculatePaceFromRunningLevel()` 한글 매직 스트링("입문자/중급자/상급자") → enum
- [ ] `PushEventListener`가 4개 도메인 이벤트를 한 클래스에서 수신 — 분리 검토

### 일관성
- [ ] 소프트삭제 4방식 → 1방식 통일 ([02-domain-model.md](02-domain-model.md))
- [ ] 리포지토리 패키지 명명 통일: `infra/persistence` vs `dao`
- [ ] 페이스 표현 VO 도입 — `"5:30"` ↔ `5.30(Double)` 손실 인코딩 반복 제거
- [ ] `Running.of()`의 `member.getRuns().contains()` 제거 — 전체 컬렉션 지연로딩 + O(n)
- [ ] `Member.runs`의 `cascade = ALL` 재검토 — 같은 영속성 컨텍스트에서 `runningRepository.delete(entity)` 호출 시 컬렉션 cascade PERSIST가 REMOVED 상태를 되돌려 **소프트삭제가 조용히 무효화됨** (2026-08-04 리드모델 테스트 중 실증. 현재 프로덕션은 벌크 삭제만 써서 미발현 — 엔티티 삭제 경로가 추가되면 발현하는 지뢰)
- [ ] `RunningCommandService` `upload()` 오버로드 중복 정리
- [ ] `NoticeApi` v1/v2/admin 컨트롤러 분리
- [ ] `PushSqsWorker` 파일 내 `@Service` 2개 분리, `Thread.sleep` 백오프 정리
- [ ] `PushService.broadcast()` 전 기기 메모리 적재 → 페이징
- [ ] 데드코드 삭제: `RedisDistributedLockManager`, Redisson 락, `CustomCourseRepositoryImpl` deprecated 메서드
- [ ] `AdminOnlyAspect` 문자열 쪼개기 정리

### 신중히 (DB 마이그레이션 수반)
- [ ] 컬럼명: `start_longtitude`(오타), `average_pace_min/km`(슬래시) — 스키마 관리 도구 도입 후에
- [ ] `CourseReadModel` 정규화 (1번에서 결정)

---

## 4. 인프라 재구성 (EB → Docker/K8s)

**목표**: Elastic Beanstalk → 컨테이너 오케스트레이션 전환. **RDS(MySQL)는 유지.**

- [ ] Dockerfile 작성 (현재 EB가 Docker를 쓰지만 리포에 Dockerfile 없음 — 빌드 산출물 확인 필요)
- [ ] K8s 매니페스트/헬름 구성: Deployment, Service, Ingress(현 ALB 대체), HPA
- [ ] 설정 외부화 — 0번(시크릿)과 연계: ConfigMap/Secret, prod의 EB 환경변수 주입 방식 대체
- [ ] Graceful shutdown 정합: 현재 `timeout-per-shutdown-phase: 480s`(LLM 완주 대기)와 K8s `terminationGracePeriodSeconds` 정렬 — 2번에서 워커 제거 후 대기 시간 재산정
- [ ] 헬스체크: 현재 `GET /` → actuator liveness/readiness probe 전환 검토 (신규 추가는 API 불변 제약과 무관)
- [ ] 로그 수집: CloudWatch appender(앱 내장) 유지 vs 사이드카/스트림 수집 전환
- [ ] CI/CD: CodePipeline → 이미지 빌드/푸시 파이프라인 재구성, GitHub Actions와 툴체인 정합(JDK 17 vs 21)
- [ ] 스키마 관리 도구(Flyway/Liquibase) 도입 — ddl-auto 의존 탈피, 3번 컬럼명 정리의 전제
- [ ] ShedLock/분산 스케줄러 필요성 재검토 (2번 결과에 따라)

---

## TODO/FIXME 핫스팟 (기존 주석, 해당 워크스트림에서 함께 처리)

- `CourseService.java:58` — Haversine/공간 타입 전환 → 1번
- `CourseRepository.java:19` — owner 필드 → 1번
- `PacemakerRateLimitService.java:94` — 보상 실패 알림 → 2번
- `PushService` — broadcast 페이징 → 3번
- `GhostRunDetailInfo.java:10` — DTO 구조 개선 → 3번
