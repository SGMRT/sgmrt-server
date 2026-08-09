# Reader/Writer 계층 규칙 — Running·Course 도메인 전면 적용 설계

> **상태: 구현 완료 (2026-08-09).** PR #171(쓰기 트랜잭션 경계를 Writer로 통일)의 후속.
> Writer로 쓰기를 모은 데 이어, **리포지토리 접근 자체를 Reader/Writer 계층으로 격리**했다.
>
> 적용 범위는 **running·course 두 도메인뿐이다.** 다른 도메인(notice, device, auth 등)은
> 트랜잭션 경계가 단순하고 리드모델도 없어 Service–Repository로 충분하며, 기계적 복제는 단순함만 잃는다.
>
> **아래 §2의 감사 표와 §4의 결정문은 설계 시점(구현 전) 기준으로 읽는다** — 구 이름은 이력 표기다.
> 구현 결과는 §7에 요약했다.

---

## 1. 계층 규칙

```
api (Controller)
 │
 ├── 쓰기 요청 ──► Service / Facade          (조율: 검증, 외부 I/O, 위임. 트랜잭션·repo 없음)
 │                  │
 │                  ├──► Writer              (모든 쓰기 트랜잭션 경계. repo 접근 허용)
 │                  └──► Reader              (트랜잭션 밖 fail-fast 조회 등)
 │
 └── 조회 요청 ──► QueryService / Facade     (조회도 진입점은 Service다 — api는 Reader를 모른다)
                    │
                    └──► Reader              (조회 전용 + 조회 TX 경계. repo 접근 허용)

Writer ──► Reader        허용 (트랜잭션 안 재조회. Reader는 호출자 TX에 참여)
Writer ──► 타 도메인 Writer  허용 (같은 TX 참여 — RunningWriter → CourseWriter.save 패턴)
Service ──► 타 도메인 Reader 허용 (Reader가 코어 — CourseFacade → RunningReader)
api ──► Reader / Writer         ❌ 금지
Service/Facade ──► Repository   ❌ 금지
Reader ──► 쓰기            ❌ 금지 (부수효과 없음을 이름으로 보장)
```

**네 줄 요약**

1. **Repository를 바라보는 클래스는 Reader와 Writer뿐이다.**
2. Service/Facade는 Reader/Writer만 바라본다 — 조율(가공·외부 I/O·위임)만 남는다.
3. **api 계층은 Service/Facade만 바라본다** — 쓰기든 조회든 Reader/Writer를 직접 부르지 않는다.
   Reader/Writer는 도메인 코어이고, api가 아는 것은 그 위의 유즈케이스 계층이다.
4. **`@Transactional`은 되도록 Reader/Writer에 둔다.** 쓰기 경계는 Writer, 조회 경계는 Reader.
   Service에 붙이는 것은 "여러 조회가 한 스냅샷이어야 한다"가 성립할 때, 그 메서드에만.

**Reader와 Service의 경계선** — Reader가 하는 일은 두 가지뿐이다: 리포지토리 호출, 그리고 그 결과를
"없으면 예외 / 키로 정규화된 뷰"로 바꾸는 것. 아래는 Reader가 아니라 Service/Facade의 몫이다.

| Reader에 두지 않는 것 | 이유 | 현재 위치 |
|---|---|---|
| 다른 도메인 조회 (`MemberService`) | 타 도메인이 Reader를 재사용할 때 딸려 들어온다 | `RunningQueryService` |
| 응답 DTO 매핑 | 응답 모양은 유즈케이스의 관심사다 | `RunningQueryService`, `CourseFacade` |
| 여러 조회의 조합·결과 검증 | 조율이지 조회가 아니다 | `RunningQueryService.findGhostRunInfo` |
| 요청 파라미터 검증 (정렬 필드 화이트리스트) | 진입점의 관심사 | `CourseFacade.findPublicGhosts` |

> **트랜잭션 경계는 이름만으로 판단하지 말 것 — 예외 3건**
>
> - **Reader의 `@Transactional(readOnly = true)`는 허용한다** (`RunningReader`). 조회 트랜잭션 경계가
>   Reader이기 때문이다. 쓰기 TX 안에서 호출되면 호출자 TX에 그대로 참여한다.
>   "Reader니까 트랜잭션이 없어야 한다"를 근거로 제거하지 말 것.
>   `open-in-view: false`이므로, **엔티티를 응답으로 매핑하는 코드가 이 트랜잭션 밖에 있다면 그 매핑이
>   지연 로딩 연관을 건드리지 않아야 한다.** 현재 그런 경로는 `CourseFacade`의 고스트 매핑뿐이고,
>   러너(`member`)가 fetch join(`RunningRepository#findByCourse_IdAndIsPublicTrue`)으로 함께 적재되어 안전하다.
> - **`CourseSubscriptionWriter`는 이름이 Writer지만 자체 TX를 열지 않는다** — 항상 호출자
>   (`CourseWriter` / `RunningWriter`)의 TX에 참여한다. (`MANDATORY` 승격은 별도 티켓 — §4 D2)
> - **`CourseReadModelWriter`도 자체 경계를 열지 않는다** — `@Transactional(propagation = MANDATORY)`라
>   활성 트랜잭션이 없으면 즉시 예외다. 위 `CourseSubscriptionWriter`와 같은 부류이며, 실제로
>   `RunningWriter.saveRun` 안에서 나란히 호출되는 짝이다. 차이는 계약이 javadoc이 아니라
>   **어노테이션으로 강제된다**는 것뿐이다.

**규칙의 적용 대상은 애플리케이션 서비스 계층이다.** 다음은 대상이 아니다:

- **리드모델·캐시 인프라 컴포넌트** (`CourseReadModelReader/Writer`, `CourseMapCacheEvictor`) —
  repo 접근이 곧 본질 책임이다. 이미 Reader/Writer로 명명되어 있고, Evictor는 커밋 후 콜백이라는
  특수 실행 문맥([R2]·[R3] 계약)을 가진 캐시 컴포넌트라 일반 Reader를 경유시키는 리팩토링은 리스크 대비 이득이 없다.
- **`RegionResolver`(현 `RegionService`)** — §4 D4 참조. 트랜잭션 없음(NEVER)이 계약이라 Writer 규칙을 적용할 수 없다.

---

## 2. 현재 상태 감사 (2026-08 기준)

| 클래스 | repo 의존 | 쓰기 여부 | 규칙 부합 |
|---|---|---|---|
| `RunningWriter` | RunningRepository | O | ✅ |
| `RunningQueryService` | RunningRepository | X | ✅ 역할은 Reader — **이름만 불일치** |
| `RunningCommandService` | 없음 | (위임) | ✅ (PR #171에서 정리 완료) |
| `PathSimplificationService` | 없음 | X | 대상 아님 (순수 도메인 계산) |
| `CourseWriter` | CourseRepository, CourseSubscriptionRepository | O | ✅ |
| `CourseQueryService` | CourseRepository | X | ✅ 역할은 Reader — **이름만 불일치** |
| `CourseFacade` | 없음 | (위임) | ✅ |
| `CourseSubscriptionService` | CourseSubscriptionRepository, CourseRepository, **MemberRepository** | O | ❌ **Service가 repo 3개 직접 접근 + 쓰기 수행** |
| `CourseReadModelReader/Writer` | CourseReadModelRepository (+CourseRepository) | R/W | ✅ 이미 패턴 그대로 |
| `CourseMapCacheEvictor` | CourseReadModelRepository | X (조회) | 적용 대상 아님 (§1) |
| `RegionService` | RegionRepository | O | 예외 (§4 D4) |

컨트롤러(api 계층)의 repo 직접 접근은 두 도메인 모두 **0건** — 어기는 곳은 사실상 `CourseSubscriptionService` 하나와, 이름이 규칙을 드러내지 못하는 `*QueryService` 둘이다.

---

## 3. 목표 구조

### running

| 클래스 | 역할 | 변경 |
|---|---|---|
| `RunningReader` (← RunningQueryService) | 조회 전용. RunningRepository를 보는 유일한 조회 클래스 | **개명** |
| `RunningWriter` | 러닝 생성·수정·삭제 쓰기 TX 경계 | 유지 |
| `RunningCommandService` | 쓰기 유즈케이스 조율 (가공·S3·VDOT·위임) | 유지 (의존 이름만 갱신) |
| `RunningApi` | 조회는 `RunningReader` 직접, 쓰기는 CommandService | 의존 이름만 갱신 |

### course

| 클래스 | 역할 | 변경 |
|---|---|---|
| `CourseReader` (← CourseQueryService) | 조회 전용. CourseRepository를 보는 유일한 조회 클래스 | **개명** |
| `CourseWriter` | 코스 저장·수정·삭제 쓰기 TX 경계. 구독은 `CourseSubscriptionWriter`에 위임 | **구독 로직 이관** |
| `CourseSubscriptionWriter` (← CourseSubscriptionService) | **구독 테이블 쓰기의 단일 지점** — 러너 구독 생성(subscribeIfAbsent) + 주인 구독 활성/복원/해제(CourseWriter에서 이관) | **개명 + 흡수** |
| `CourseFacade` | 조회 조립 + 쓰기 위임 | 의존 이름만 갱신 |
| `RegionResolver` (← RegionService) | 이름→지역 멱등 해소. Reader도 Writer도 아닌 예외 컴포넌트 | **개명** (§4 D4) |
| `CourseReadModelReader/Writer`, `CourseMapCacheEvictor` | 리드모델·캐시 인프라 | 변경 없음 |

### 목표 의존 그래프 (쓰기 경로)

```
RunningCommandService ──► RunningWriter ─┬─► RunningRepository
                                         ├─► CourseWriter ──► CourseRepository
                                         ├─► CourseReader (TX 안 재조회)
                                         ├─► CourseSubscriptionWriter ──► CourseSubscriptionRepository
                                         ├─► CourseReadModelWriter (MANDATORY)
                                         └─► CourseMapCacheEvictor (커밋 후 예약)

CourseFacade ──► CourseWriter ─┬─► CourseRepository
                               ├─► CourseSubscriptionWriter (주인 구독 위임)
                               ├─► CourseReadModelWriter
                               └─► CourseMapCacheEvictor
```

---

## 4. 결정 사항

### D1. `*QueryService` → `*Reader` 개명

용어를 Reader/Writer 한 쌍으로 통일한다. 리드모델 쪽이 이미 `CourseReadModelReader`라 저장소 전체에서
"Reader = 조회 전용, repo 접근 허용"이라는 어휘가 완성된다.

- **트레이드오프**: `CourseQueryService`는 PR #171에서 방금 만든 이름이라 연속 개명 churn이 있다.
  그러나 두 이름 체계(QueryService/Reader)가 공존하는 비용이 더 크고, 개명은 아직 이름이 굳기 전인 지금이 가장 싸다.
- 호출자: RunningApi, RunningCommandService, RunningWriter, CourseFacade, CourseWriter,
  PushEventListener, PacemakerValidator + 테스트 5곳. 전부 기계적 치환.

### D2. 구독 쓰기를 `CourseSubscriptionWriter`로 단일화

현재 CourseSubscription 테이블 쓰기가 **두 곳**이다 — 러너 구독은 `CourseSubscriptionService.subscribeIfAbsent`,
주인 구독은 `CourseWriter`의 private 메서드(activate/deactivateOwnerSubscription). 같은 테이블의 쓰기가
쪼개져 있으면 멱등 규칙·소프트delete 복원 규칙이 어긋나기 시작할 때 원인 추적이 두 배가 된다.

- `CourseSubscriptionService` → `CourseSubscriptionWriter` 개명.
- `CourseWriter`의 주인 구독 활성/복원/해제 로직을 이관하고 CourseWriter는 위임한다.
  (Writer→Writer 호출, 같은 TX 참여 — RunningWriter→CourseWriter.save와 동일한 패턴)
- 자체 `@Transactional`은 두지 않는다(현행 유지) — 항상 호출자(CourseWriter/RunningWriter)의 TX 안에서 실행된다.
  독립 호출을 막으려면 추후 `MANDATORY` 승격을 검토한다.
- **대안(기각)**: CourseWriter로 전부 흡수 — 구독은 응집된 하위 개념이고 CourseWriter가 이미 200줄이라
  단일 클래스 비대화보다 분리 유지가 낫다.

### D3. 타 도메인 repo 직접 접근 제거 (`MemberRepository`)

`CourseSubscriptionService`가 러너 구독 생성 시 `MemberRepository`를 직접 조회한다. 규칙상 course의
Writer가 볼 수 있는 repo는 course 소유분뿐이다. `MemberService.findMemberById`(이미 존재)로 교체한다.

- **트레이드오프**: 예외 타입이 `MemberNotFoundException(ENTITY_NOT_FOUND)`로 동일하게 유지되는지 확인 필요.
  MemberService 경유 시 Member의 soft delete 필터 등 조회 의미가 달라지지 않는지 구현 시 검증한다.

### D4. `RegionService` → `RegionResolver` — Reader/Writer 예외로 명시

resolve(조회+등록 멱등 연산)는 읽기도 쓰기도 아닌 혼합이고, 무엇보다 **`@Transactional(NEVER)`가 계약**이다
(유니크 충돌 → 재조회 복구가 트랜잭션 오염 없이 동작해야 함, 클래스 javadoc 참조). "Writer = 쓰기 TX를 연다"는
규칙을 적용하면 이 계약과 정면충돌하므로, Writer로 개명하지 않고 **Resolver**라는 역할 이름으로 예외임을 드러낸다.

### D5. 조회 진입점은 Reader 직접 호출을 허용 — ❌ **철회됨 (§8 참조)**

> 원안: 쓰기는 반드시 Service/Facade를 거치지만, 조회는 조립이 없으면 api가 Reader를 직접 불러도 된다.
> 조회마다 Facade를 강제하면 한 줄 위임 메서드만 늘어난다.
>
> **이 결정은 §8에서 뒤집혔다.** 판단 근거가 틀렸다 — `RunningReader`는 "조립이 없는" 클래스가 아니었다.

---

## 5. 리팩토링 단계

한 PR로 가능한 규모지만, 리뷰 단위를 위해 커밋을 나눈다.

| 단계 | 내용 | 파급 |
|---|---|---|
| 1 | `RunningQueryService` → `RunningReader`, `CourseQueryService` → `CourseReader` 개명 (git mv) | 호출자 7곳 + 테스트, 기계적 |
| 2 | `CourseSubscriptionService` → `CourseSubscriptionWriter` 개명 + CourseWriter 주인 구독 로직 이관 + MemberService 경유 전환 | CourseWriter, RunningWriter, 단위 테스트 재배치 |
| 3 | `RegionService` → `RegionResolver` 개명 | 호출자(RegionService 소비처) 확인 후 치환 |
| 4 | 문서 동기화 — 이 문서 상태 갱신, docs/core/03-architecture.md, 관련 javadoc 상호 참조 | /sync-docs |

**동작 변화 0이어야 한다.** 각 단계는 트랜잭션 경계·전파·예외 타입을 바꾸지 않는다.
검증: `./gradlew build`(전체 테스트) + 단계 2는 `CourseWriterUnitTest`·`RunningWriterTest`의
구독 관련 검증이 새 배치에서 그대로 통과하는지 확인.

---

## 6. 하지 않는 것

- **다른 도메인 적용** — member, notice, device, auth, pacemaker는 대상이 아니다. 특히 member는
  `MemberVdotWriter`가 이미 있지만 MemberService 전면 재편은 별도 판단.
- **리포지토리 인터페이스 추상화(헥사고날)** — QueryDSL·fetch join·영속성 컨텍스트 제어를 포트 뒤로
  숨기는 비용이 이 규모에서 이득을 넘는다.
- **Reader의 CQRS 모델 분리** — 조회 성능 문제는 리드모델(셀 버킷 캐시)이 이미 해결한 범위까지만.

---

## 7. 구현 결과 (2026-08-09)

전체 빌드·테스트 그린(717 tests, 실패 0). **동작 변화 0** — 트랜잭션 경계·전파·예외 타입·외부 API 전부 불변.

> **단 하나의 예외는 관측이다.** `SentrySpanAspect`의 포인트컷이 클래스 이름 접미사에 의존해,
> 개명한 클래스들이 APM 계측에서 이탈했다(리뷰 iter1 MAJOR-1). 접미사를 추가해 복구했는데
> 그 과정에서 계측 대상이 **7개 → 11개**가 됐다 — 복구분 7개에 더해 `CourseWriter`·`RunningWriter`·
> `CourseReadModelReader`·`CourseReadModelWriter` 4개가 새로 포함됐다. 직전 PR에서 생긴 Writer들까지
> 규칙에 맞게 계측되는 것이 옳다고 판단했으나, 런타임 동작(스팬 생성)이 늘어난 것은 사실이므로 기록해 둔다.
> 포인트컷을 이름이 아닌 패키지/스테레오타입 기준으로 재설계하는 것은 별도 티켓(DEFER-2)이다.

| 결정 | 구현 |
|---|---|
| D1 | `RunningQueryService` → `RunningReader`, `CourseQueryService` → `CourseReader` (git mv, 이력 보존) |
| D2 | `CourseSubscriptionService` → `CourseSubscriptionWriter`. `CourseWriter`의 `activate/deactivateOwnerSubscription`을 이관받아 **구독 테이블 쓰기의 단일 지점**이 됨. `CourseWriter`는 위임만 하고 `CourseSubscriptionRepository` 의존 제거 |
| D3 | `CourseSubscriptionWriter`의 `MemberRepository` → `MemberService.findMemberById` |
| D4 | `RegionService` → `RegionResolver`. `@Transactional(NEVER)` 유지 |
| D5 | 조회 진입점은 Reader 직접 호출 허용 — 현행 유지 → **§8에서 철회** |

### 설계 시점과 달라진 판단

- **이관 메서드 시그니처는 `(Course course)`로 확정.** `(courseId, ownerId)`로 바꾸면 `CourseSubscription.create(course, member)`를 위해 코스·멤버를 재조회하게 되어 쿼리가 늘고 "동작 변화 0"을 깬다.
- **통합 검증 1건 추가.** 주인 구독의 소프트delete 복원은 "깨지면 실제로 아픈" 불변식인데 DB 레벨 검증이 0건이었다. 이관 **전에** 회귀 테스트를 먼저 통과시켜 안전망으로 삼았고(`CourseWriterTest.ownerSubscription_isRestoredNotDuplicated_onReRegister`), 이관 후에도 통과하는 것이 동작 변화 0의 판정 기준이 됐다.
- **NEVER 전파 계약 테스트 신설.** 개명 전에는 이 계약을 검증하는 테스트가 0건이라, 어노테이션이 유실돼도 아무 테스트도 깨지지 않았다. `RegionResolverPropagationTest`가 그 공백을 메운다.
- **`subscribeIfAbsent`(러너)와 `activateOwnerSubscription`(주인)의 이름은 통일하지 않았다.** 멱등 규칙이 다르기 때문이다 — 러너 구독은 soft delete 상태여도 그대로 두고, 주인 구독은 복원한다. 같은 이유로 두 경로의 `findByCourseIdAndMemberId` 중복도 통합하지 않았다(규칙 차이가 플래그 뒤로 숨는다). 대비되는 javadoc으로 대신했다.

---

## 8. 후속 — D5 철회와 `RunningQueryService` 재도입 (2026-08-09)

### 무엇이 틀렸나

D1에서 `RunningQueryService`를 `RunningReader`로 개명했는데, **그 클래스는 Reader가 아니었다.**
개명 시점에 이미 아래를 들고 있었다:

- `MemberService` 의존 — uuid→Member 해소 (`findRunnings`, `findMonthlyDayRunStatus`)
- `RunningApplicationMapper` 의존 — 엔티티 → 응답 DTO 매핑
- 다중 조회의 조합과 그 결과 검증 (`findGhostRunInfo`: 내 러닝 조회 → 고스트ID 대조 → 고스트 기록 조회)
- 요청 파라미터 검증 (`validateSortProperty` — 그나마 course 도메인의 `GhostSortType`)

즉 "조립이 없으니 api가 Reader를 직접 불러도 된다"(D5)의 전제부터 성립하지 않았다.
조립은 있었고, 단지 Reader라는 이름 뒤에 있었을 뿐이다. **개명이 역할을 바꾸지는 않는다.**

### 무엇을 했나

| 구분 | 변경 |
|---|---|
| `RunningReader` | 리포지토리 호출 + 예외 변환 + 키 정규화만 남김. `MemberService`·매퍼 의존 **제거**. `@Transactional(readOnly = true)` 유지(조회 TX 경계) |
| `RunningQueryService` | **신규.** `RunningReader`+`MemberService`+매퍼를 주입받아 조회 유즈케이스를 조율. `RunningCommandService`와 대칭 |
| `RunningApi` | `RunningReader` → `RunningQueryService` (조회 6개 엔드포인트) |
| `CourseFacade` | 고스트 조회의 정렬 검증·`CourseGhostResponse` 매핑·상위 퍼센트 계산을 흡수. Reader에서는 `Page<Running>`과 count만 받는다 |

`CourseGhostResponse`도 `GhostSortType`도 **course 도메인의 타입**이다. 이들을 running의 Reader가
조립하고 있던 것 자체가 방향이 뒤집힌 의존이었고, 이번에 CourseFacade로 제자리를 찾았다.

### 트랜잭션 배치

`@Transactional`은 **Reader/Writer에 두고, Service에는 원칙적으로 두지 않는다.**
`RunningQueryService`에는 클래스·메서드 어디에도 붙이지 않았다 — 어느 메서드도
"여러 조회가 한 스냅샷이어야 한다"를 요구하지 않기 때문이다. `findGhostRunInfo`조차
첫 조회 결과로 검증을 끝낸 뒤 두 번째를 던지므로 순차 실행으로 충분하다.

- **대가**: 조회 1건이 열던 트랜잭션이 (회원 조회 / 러닝 조회) 2개로 쪼개진다.
  읽기 전용이고 교차 정합성 요구가 없어 의미 있는 동작 변화는 없다.
- **지켜야 할 전제**: `open-in-view: false`다. Reader 트랜잭션 밖에서 엔티티를 매핑하는 경로는
  현재 `CourseFacade`의 고스트 매핑뿐이고, `member`가 fetch join으로 적재되어 안전하다.
  이 전제가 깨지는 매핑을 추가한다면 **그 메서드에만** `@Transactional(readOnly = true)`를 붙인다.

### 하지 않은 것

- **course 도메인 구조 변경 없음.** `CourseApi → CourseFacade → CourseReader/CourseWriter`는 이미 이 규칙을 지키고 있었다.
- **`CourseQueryService` 재도입 안 함.** 조회 진입점 역할은 `CourseFacade`가 이미 하고 있고, Facade는 쓰기 위임도 겸한다. 이름만 다른 같은 계층을 하나 더 만들 이유가 없다.
- **`RegionApi → RegionResolver`는 그대로.** Resolver는 Reader/Writer가 아닌 서비스 계층 컴포넌트(§4 D4)이므로 api가 직접 호출해도 규칙 위반이 아니다.
- **`RunningFacade` 도입 안 함.** 조회는 `RunningQueryService`, 쓰기는 `RunningCommandService`로 이미 대칭이라 그 위에 한 겹 더 얹을 이득이 없다.

### 테스트

`RunningReaderTest`는 리포지토리 위임·예외 변환·키 정규화만 보도록 축소하고,
조합·매핑·타 도메인 조회 검증은 `RunningQueryServiceTest`로 옮겼다.
정렬 화이트리스트 검증은 `CourseFacadeTest`로 이동했다 — 기존 테스트는 스파이로 대상 메서드 자체에
예외를 주입하고 있어 사실상 아무것도 검증하지 않았다.
