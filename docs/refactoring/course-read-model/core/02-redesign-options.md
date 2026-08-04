# CourseReadModel 재설계 — TOP4 저장 구조 방안 비교

> [01-current-state.md](01-current-state.md)의 후속. 2026-08-03 문답으로 확정된 요구사항 기반.
> 정확성 요구가 "최종적 일관성"에서 **"즉시 반영(강한 일관성)"으로 변경**되어 재설계됨. C(JSON)는 프로필 스테일 문제로 탈락.
>
> **✅ 최종 결정 (2026-08-03): 방안 A (8컬럼 + RankSlot VO) 확정.** 상세 구조 비교는 [03-structure-a-vs-b.md](03-structure-a-vs-b.md).
> **결정 근거**: 지도 카드의 TOP4는 "랭킹"이라는 도메인 개념이 아니라 **메인 화면 전용 뷰 데이터** — CQRS 리드모델은 뷰 모양대로 저장하는 것이 원칙이므로 한 행에 펼쳐진 구조가 정합적. B의 1:N 정규화는 뷰 데이터의 과잉 도메인 모델링. 추후 진짜 랭킹 기능(리더보드)이 생기면 그 요구사항에 맞는 별도 구조(Sorted Set/랭킹 테이블)를 신설하고 지도 리드모델은 불변으로 둔다.
> ⚠️ **요구사항 정정 반영 (탈퇴 러너 메인화면 유지)**: 본문 중 탈퇴 회원 필터링·정정 흐름 관련 서술(집계 조건의 "미탈퇴" 항, 탈퇴 정정 비교·권고)은 이후 요구사항 정정으로 **폐기됨** — 탈퇴 러너도 TOP4에 유지되며 member JOIN의 `deleted_at` 필터는 최종 구현에서 제거됐다. 상세는 [03-structure-a-vs-b.md](03-structure-a-vs-b.md) 상단 및 [04-detailed-design.md](04-detailed-design.md) 최종 정책 참조.
>
> 단, 러너 프로필(uuid/사진)은 역정규화하지 않고 조회 시 member 4-alias LEFT JOIN 유지 — 프로필 변경·탈퇴 즉시 반영을 위해. "조인 제로"는 프로필 역정규화(C 성질)가 필요해 채택하지 않음.

## 1. 확정 요구사항

| 항목 | 결정 |
|---|---|
| 랭킹 소비처 | **지도 API의 TOP4 전용** — /top-ranking(50명), /ranking(전체 순위)은 기존 running 집계 유지. **N=4 고정 설계 허용** |
| 정확성 | **즉시 반영 (강한 일관성)** — 러닝 삭제/비공개 전환이 **같은 트랜잭션 안에서** TOP4·runnersCount에 반영되어야 함 |
| 탈퇴 러너 | **탈퇴해도 메인화면(TOP4)에 계속 남는다** — 탈퇴 정정 경로 불필요. 조회 시 member JOIN의 `deleted_at IS NULL` 필터 제거 (⚠️ 추후 개인정보 파기 정책 도입 시 익명화 표시 재검토) |
| 응답 필드 | 코스 필드(거리, 고도 3종, thumbnailUrl)를 **리드모델에 역정규화**. `checkpointsUrl`·`createdAt`은 클라 미사용 확인 → 리드모델에 넣지 않고 **응답 필드는 유지하되 null** (스펙상 필드 제거 아님) |
| 백필 | 기존 공개 코스 대상 **일회성 마이그레이션** |
| 필터/정렬/구독 | 클라 미사용 — **최소 구현만** |
| 캐시 | 수동 Redis 캐시 제거, **Spring Cache(RedisCacheManager) 도입** |
| 기존 경로 | `findCoursesByPositionCached` + `CourseCacheRepository`는 @Deprecated 후 제거 수순 |

핵심 성질: 읽기 ≫ 쓰기. 쓰기는 ①러닝 종료(증분) ②러닝 삭제/비공개 전환 ③회원 탈퇴 ④코스 변경(이름/공개/삭제) 시 발생하며, **②③④ 모두 즉시 정정 경로가 필요**하다.

## 2. 즉시 정정이 요구하는 쓰기 경로 (현재 누락분 포함)

현재 코드에서 리드모델을 정정하지 않는(혹은 이벤트조차 없는) 지점:

| 트리거 | 현재 상태 | 필요한 설계 |
|---|---|---|
| 러닝 종료 | `RunFinishedEvent` → BEFORE_COMMIT 증분 갱신 ✅ | Writer 직접 호출로 전환 (`applyRun` — 증분 유지) |
| 러닝 이름/공개 전환 | `RunUpdatedEvent` 발행되나 **캐시 무효화만 소비** | `updateRunningPublicStatus()`에서 Writer **직접 호출** → TOP4 재계산 + runnersCount 재집계 |
| 러닝 삭제 (벌크) | `deleteRunnings()`가 **아무 정정도 안 함** — 캐시·리드모델 모두 미정정 | 삭제 러닝의 courseId를 알고 있으므로 Writer **직접 호출** (`recalculate(courseIds)`) — 이벤트 불필요 |
| 회원 탈퇴 | TOP4에 탈퇴 회원 잔존 | **정정하지 않음 (요구사항)** — 탈퇴 러너도 메인화면에 유지. 조회 쿼리의 `deleted_at IS NULL` 필터만 제거 |
| 코스명 변경 | 리드모델 name 미동기화 | `CourseService.updateCourseName`에서 동기화 |
| 코스 공개/비공개/삭제 | 동기화 존재 ✅ | 유지 |

**재계산 쿼리** (단일 코스): `running_record`에서 `is_public=true AND deleted=false AND course_id=? AND member(미탈퇴)` 를 member별 MIN(duration) 집계 → 상위 4 → `replaceTop4()`. PR #101의 `idx_record_course (is_public, deleted, course_id, member_id, duration_sec)` 인덱스로 루즈 인덱스 스캔이 가능해 코스당 비용이 낮다.

~~회원 탈퇴 시 영향 코스 역조회~~ — **요구사항 변경(탈퇴 러너 메인화면 유지)으로 이 경로 자체가 소멸.** 방안 간 탈퇴 정정 정밀도 비교는 무의미해짐.

## 3. TOP4 저장 구조 방안

### 방안 A. 8컬럼 유지 + `RankSlot` VO 변환 (이전 실험 방향)

컬럼 구조(top1~4_member_id, top1~4_time_seconds)는 유지하되, 엔티티 내부에서 `List<RankSlot>`로 변환해 삽입/재정렬을 리스트 연산으로 처리하고 다시 컬럼에 써넣는다.

```java
record RankSlot(Long memberId, Integer timeSeconds) implements Comparable<RankSlot> { ... }

public boolean insertIfBetter(Long memberId, int timeSeconds) {
    List<RankSlot> slots = toSlots();                  // 8컬럼 → 리스트
    boolean changed = RankSlots.insert(slots, new RankSlot(memberId, timeSeconds), MAX_RANK);
    if (changed) applySlots(slots);                     // 리스트 → 8컬럼
    return changed;
}

public void replaceAll(List<RankSlot> recalculated) { applySlots(recalculated); }  // 재계산 반영
```

| 장점 | 단점 |
|---|---|
| **DB 마이그레이션 최소** — 스키마 그대로, 백필 단순 | 탈퇴 정정 시 영향 코스를 **러닝 기반 역조회**로 근사 (TOP4에 없던 코스도 재계산 — 낭비 있으나 저빈도) |
| 조회 쿼리 구조 유지 — 1행 스캔 + member LEFT JOIN×4 | N 변경 시 컬럼 추가 필요 (N=4 고정 확정이라 무해) |
| 단일 행 X락 — 증분·재계산 모두 레코드락 1개로 동시성 명확 | 컬럼 8개 반복 구조와 VO 매핑 코드는 남음 |
| 200줄 시프트/스와프 → 리스트 연산 ~30줄, 재계산도 `replaceAll` 한 방 | |
| 멤버 프로필은 조회 시 JOIN이라 항상 신선 (탈퇴 시 deleted_at 필터도 동작) | |

### 방안 B. 별도 랭킹 테이블 (1:N 정규화)

```sql
CREATE TABLE course_ranking (
  id BIGINT PK, course_id BIGINT, `rank` INT, member_id BIGINT, time_seconds INT,
  UNIQUE KEY uk_course_rank (course_id, `rank`),
  KEY idx_member (member_id)                -- 탈퇴 정정용 역조회
);
```

| 장점 | 단점 |
|---|---|
| **탈퇴 정정이 정확·즉시** — `WHERE member_id=?`로 TOP4에 실제 든 코스만 인덱스 조회 | **조회 시 JOIN 1개 추가** (rm ⋈ ranking ⋈ member) — 코스 50개당 랭킹 ≤200행 |
| 순위 로직 최단순 — 재계산 = delete+insert, 시프트 개념 소멸 | 쓰기 시 다중 행 갱신 — 코스당 최대 4행 락 + (course_id, rank) UK 갱신 순서 주의 |
| N 확장 자유 | 행 수 4배, 고아 행 관리 지점 추가 |
| | DB 마이그레이션 + 백필 로직 커짐 |

### 방안 C. JSON 컬럼 (러너 프로필까지 내장)

`@JdbcTypeCode(SqlTypes.JSON) List<TopRunner> topRunners` — member uuid/프로필 URL까지 내장해 조회 JOIN 0회.

| 장점 | 단점 |
|---|---|
| 조회가 리드모델 단독 스캔으로 완결 (JOIN 0) | **즉시 반영 요구와 정면 충돌** — 프로필 변경·탈퇴가 JSON에 반영 안 됨. member 변경 이벤트 동기화까지 필요해져 범위가 member 도메인으로 확대 |
| 순위 로직 = 리스트 연산 | 탈퇴 정정 역조회 불가(JSON 검색) → 러닝 기반 역조회 필요 |
| 컬럼 1개로 스키마 단순 | JSON 스키마 진화/호환성 관리 |

### 방안 B 심화 분석 (즉시 반영 요구 기준 상세 트레이드오프)

#### B-1. 조회 전략 — 두 가지 구현이 가능

**(a) 단일 JOIN 쿼리**: `rm ⋈ course_ranking ⋈ member` — 결과가 (코스×랭크)로 행 폭발(50코스 → ≤200행)해 앱에서 그룹핑 필요. 네이티브 쿼리 유지 시 매핑 복잡.

**(b) 2쿼리 + 배치 (권장 구현)**: ① `rm` 바운딩박스 스캔(50행) ② `course_ranking ⋈ member WHERE course_id IN (...)` — JPA `@OneToMany @OrderBy("rank")` 매핑이면 `default_batch_fetch_size: 100` 설정이 이미 있어 **IN 배칭이 자동**. 쿼리 2방으로 고정되고 코드가 깔끔.

→ 조회 비용: A(1쿼리 + 4-alias LEFT JOIN) vs B-(b)(2쿼리). 코스 수십 개 규모에선 실질 차이 미미. "조회 1방"이라는 A의 우위는 (b) 채택 시 "1방 vs 2방"의 차이로 축소.

#### B-2. 동시성 — 락 프로토콜을 지키면 A와 동일하게 만들 수 있음

- 위험: `(course_id, rank)` UK에 여러 트랜잭션이 동시에 delete+insert하면 갭락/UK 충돌로 데드락 가능.
- 해법: **모든 쓰기가 부모(`course_read_model`) 행 X락을 먼저 잡는 프로토콜 유지** (`findByCourseIdForUpdate` — 현행 그대로). 부모 락이 직렬화 앵커가 되므로 자식 행 경합이 원천 차단됨. 즉, 락 모델은 A와 동일하게 유지 가능하며, 단지 "프로토콜을 지켜야 한다"는 규율이 하나 추가됨.

#### B-3. 쓰기 증폭

- A: 랭킹 변동 시 1행 UPDATE (컬럼 8개).
- B: 랭킹 변동 시 최대 4행 delete+insert (orphanRemoval) 또는 변경분만 UPDATE. 인덱스 2개(uk, member_id) 유지 비용 추가.
- 러닝 종료 빈도 자체가 낮은 서비스라 절대량은 작지만, 구조적으로 A가 가볍다.

#### B-4. JPA 애그리거트 모델링 — B의 숨은 강점

```java
@OneToMany(mappedBy = "readModel", cascade = ALL, orphanRemoval = true)
@OrderBy("rank ASC")
private List<CourseRanking> rankings;   // 코스당 ≤4

public void applyRun(Long memberId, int time)  { /* 리스트 연산 후 재번호 */ }
public void replaceAll(List<RankEntry> recalc) { rankings.clear(); rankings.addAll(...); }
```

- 순위 = "정렬된 컬렉션"이라는 도메인 개념이 그대로 코드가 됨. **A의 RankSlot VO가 하는 8컬럼↔리스트 변환 계층 자체가 사라짐.**
- `orphanRemoval`로 delete+insert가 애그리거트 저장 한 번에 처리됨.

#### B-5. 정정 정밀도 (즉시 반영의 핵심 경로)

- 탈퇴: `WHERE member_id = ?` 인덱스 조회로 **TOP4에 실제 든 코스만** 정정. A는 탈퇴자의 러닝이 있는 모든 코스를 재계산(활동적인 러너 탈퇴 시 수십 코스 과잉 재계산 — 저빈도라 허용 범위이긴 함).
- 러닝 삭제/공개 전환: courseId를 아는 경로라 A·B 동일.

#### B-6. 마이그레이션/백필 — 생각보다 차이가 작음

- 백필이 **어차피 running_record에서 전체 재계산**하므로, 8컬럼 데이터를 새 테이블로 "이관"할 필요가 없음. B의 추가 작업은 테이블 1개 생성(+추후 8컬럼 drop)뿐.
- 스키마 관리 도구가 없는 현 상태에선 A(ALTER ADD 컬럼)든 B(CREATE TABLE)든 수동 DDL이 필요한 건 동일.

#### A vs B 최종 판단 기준

| 무엇을 중시하나 | 선택 |
|---|---|
| 변경 최소화, 현행 쿼리·락 유지, 단순한 diff | **A** |
| 도메인 모델 순도(순위=컬렉션), 정정 정밀도, 매핑 계층 제거 | **B** |
| 쓰기 비용 최소 | A (근소) |
| 조회 비용 | A 1쿼리 vs B 2쿼리 — 실질 동급 |
| 동시성 | 동일하게 만들 수 있음 (B는 부모 락 프로토콜 규율 필요) |

즉시 반영 요구로 설계의 중심이 "재계산·정정 경로"가 된 이상, **그 경로가 가장 깔끔한 B의 실질 가치가 처음 평가보다 높아졌다.** 리팩토링 목적이 구조 개선임을 감안하면 B 채택도 충분히 합리적.

### 방안 D. Redis Sorted Set 랭킹 원본 (기각)

랭킹 내구성 원본이 Redis가 되어 유실/재빌드/정합성 복잡도가 과함. **즉시 반영 요구에서는 DB 트랜잭션과 Redis 갱신의 원자성 문제까지 추가**되므로 기각 사유가 더 강해짐.

## 4. 비교 요약 (즉시 반영 기준)

| 기준 | A. 8컬럼+VO | B. 별도 테이블 | C. JSON |
|---|---|---|---|
| 러닝 종료 증분 갱신 | ✅ 1행 | ✅ ≤4행 | ✅ 1행 |
| 러닝 삭제/비공개 재계산 | ✅ courseId 알므로 동일 | ✅ | ✅ |
| **회원 탈퇴 즉시 정정** | 🔶 러닝 기반 역조회 (근사, 저빈도라 허용 가능) | ✅ member_id 인덱스 정확 조회 | ❌ 역조회 불가 + 프로필 스테일 |
| 멤버 정보 신선도 (즉시성) | ✅ 조회 시 JOIN | ✅ | ❌ |
| 조회 쿼리 | 1행 + member JOIN×4 | +랭킹 JOIN | 단독 (그러나 위 ❌) |
| 마이그레이션/백필 부담 | **최소** | 큼 | 중간 |
| 동시성 | 1행 X락 (현행 유지) | 다중 행 락 | 1행 X락 |

## 5. 추천

**여전히 방안 A를 추천**하되, 즉시 반영 요구로 B와의 격차가 줄었음을 명시한다.

- **C는 탈락** — 프로필 내장이 즉시 반영 요구와 정면 충돌.
- A vs B의 실질 차이는 **회원 탈퇴 정정의 정밀도**뿐: A는 탈퇴 회원의 러닝이 있는 코스를 전부 재계산(과잉 재계산 있음), B는 TOP4에 든 코스만 정확 재계산. 탈퇴는 저빈도 이벤트이고 재계산 쿼리는 코스당 인덱스 스캔이라, 이 차이로 B의 마이그레이션·조인·다중행 락 비용을 감수할 이유는 부족하다.
- 러닝 삭제/비공개 전환은 이벤트에 courseId가 있어 **A·B 모두 동일한 비용** — 선택에 영향 없음.
- A는 스키마·조회 쿼리·락 모델을 유지한 채 로직만 단순화하므로, "즉시 정정 경로 추가"라는 이번 핵심 변경에 리스크를 더하지 않는다.

## 6. 동기화 방식 결정: 스프링 이벤트 vs 직접 컴포넌트 호출

### 현재 이벤트 쓰임새 전수 조사

| 이벤트 | 리스너 | 페이즈 | 하는 일 |
|---|---|---|---|
| RunFinishedEvent | member.RunFinishedEventListener | BEFORE_COMMIT | VDOT 갱신 (running→member 크로스 도메인) |
| 〃 | course.ReadModelSyncListener | BEFORE_COMMIT | **리드모델 증분 갱신** ← 이번 검토 대상 |
| 〃 | course.CourseCacheEventListener | AFTER_COMMIT | 캐시 무효화 |
| RunUpdatedEvent | course.CourseCacheEventListener | AFTER_COMMIT | 캐시 무효화 |
| CourseRunEvent | course.CourseSubscriptionEventListener | BEFORE_COMMIT | 구독 멱등 생성 |
| 〃 | notification.PushEventListener ×2 | AFTER_COMMIT | 푸시 (내 코스 남이 달림 / 최고기록) |

### 판단: 리드모델 동기화는 **직접 컴포넌트 호출**로 전환

BEFORE_COMMIT 리스너는 같은 스레드·같은 트랜잭션의 동기 호출이다. 이벤트의 실질 이득(비동기, 페이즈 제어, 크로스 도메인 디커플링) 중 리드모델 동기화에 해당하는 것이 없다:

1. **디커플링할 결합이 아님** — `RunningCommandService`는 이미 `courseService.save()/findCourse()`를 직접 호출하고, `Running`은 `@ManyToOne Course`다. running→course 의존은 실재하며 자연스러움. 이벤트는 이 결합을 없애는 게 아니라 **숨길 뿐**이다.
2. **강한 일관성 요구와 직접 호출이 정합** — 즉시 반영은 호출 순서·실패 전파가 명확해야 한다. 직접 호출은 스택트레이스와 순서가 자명하고, 리스너는 실행 순서 비보장 + 트랜잭션 없는 곳에서 발행 시 **조용히 스킵**되는 함정이 있다.
3. **트리거 증가 시 비용 역전** — 즉시 정정으로 트리거가 4종(종료/공개전환/삭제/탈퇴)으로 늘었다. 이벤트 방식이면 이벤트 2종 신설 + 리스너 3종. 직접 호출이면 course에 Writer 컴포넌트 1개 + 호출부 몇 줄.
4. **테스트 단순** — Writer는 단위 테스트 대상, 리스너는 이벤트 발행 + 트랜잭션 컨텍스트가 필요.

### 유지할 이벤트 (실질 이득이 있는 곳)

- **AFTER_COMMIT 부수효과**: 푸시(외부 I/O는 TX 밖 필수), 캐시 무효화(커밋 후 무효화가 스테일 재적재 레이스에 안전) — 트랜잭셔널 이벤트의 페이즈 기능을 실제로 쓰는 곳.
- **member VDOT** (running→member): 도메인 경계를 넘고, running이 member의 VDOT 정책을 알 필요가 없음 — 유지 (이번 범위 밖).
- ~~회원 탈퇴 → 리드모델 정정~~: **요구사항 변경(탈퇴 러너 유지)으로 소멸** — 신규 이벤트 0개, 리드모델 동기화는 100% 직접 호출로 통일.
- CourseRunEvent 구독 생성(BEFORE_COMMIT)도 같은 논리로 직접 호출 전환 후보지만, **이번 워크스트림 범위 밖** (3번 워크스트림에서).

## 7. 방안 확정 후 전체 재설계 스케치 (다음 문서에서 상세화)

1. **엔티티**: TOP4 로직 VO(RankSlot)화 + `replaceAll(재계산 결과)` + 응답 필드 역정규화 추가(distance, elevationAverage/Gain/Loss, thumbnailUrl — 5개. checkpointsUrl/createdAt은 응답에서 null 유지)
2. **즉시 정정 쓰기 경로 — course 도메인의 `CourseReadModelWriter` 컴포넌트로 일원화** (호출자 트랜잭션 참여, X락):
   - `applyRun(courseId, memberId, duration)` — 러닝 종료 시 증분 갱신. `RunningCommandService`가 직접 호출 (기존 `ReadModelSyncListener` 제거)
   - `recalculate(courseIds)` — 러닝 삭제(`deleteRunnings`)·공개 전환(`updateRunningPublicStatus`) 시 직접 호출. 재계산 쿼리(루즈 인덱스 스캔) → `replaceAll`
   - `rename(courseId, name)` / 공개·비공개·삭제 동기화 — `CourseService`에서 호출 (기존 로직 Writer로 이동)
   - ~~회원 탈퇴 정정~~ — **요구사항 변경(탈퇴 러너 메인화면 유지)으로 제거. 신규 이벤트 0개.** 조회 쿼리의 member JOIN에서 `deleted_at IS NULL` 필터 제거로 대응
3. **조회 전환**: `findCoursesForMap` 확장(역정규화 필드 포함) → API 응답 스펙 완전 충족(내 고스트는 선별된 10개만 별도 조회) → `CourseApi` 전환
4. **Spring Cache**: RedisCacheManager + `@Cacheable`/`@CacheEvict`. 무효화 트리거를 위 2의 쓰기 경로와 정렬 (러닝 종료/수정/삭제, 코스 변경, 회원 탈퇴)
5. **백필**: 공개 코스 전체 리드모델 생성 일회성 마이그레이션 (재계산 쿼리 재사용)
6. **Deprecated/제거**: `findCoursesByPositionCached`, `CourseCacheRepository`, `ReadModelSyncListener`, `CourseCacheEventListener`(신규 무효화 체계로 대체), `findCourseIdsWithFilters`
