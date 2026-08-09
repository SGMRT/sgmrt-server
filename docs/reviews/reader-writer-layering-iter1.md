# 코드 품질 리포트 — Reader/Writer 계층 규칙 전면 적용 (iter1)

- **리뷰 범위**: `b319233..HEAD` (커밋 2개 — `e11a88d` 구현, `acc8a0e` 문서 동기화), 45 files / +902 / -459
- **설계 문서**: `docs/design/reader-writer-layering.md`
- **작업 성격**: 순수 구조 리팩토링. 신규 기능 없음. **판정 기준은 "동작이 정말 안 바뀌었는가"**
- **리뷰 일자**: 2026-08-09

---

## 총평

먼저 잘한 것부터. 이 PR은 제가 리뷰해 본 리네이밍 리팩토링 중 상위권입니다. 이유가 세 가지 있습니다.

첫째, **이관 전에 안전망을 먼저 깔았습니다.** `CourseWriterTest.ownerSubscription_isRestoredNotDuplicated_onReRegister`를 이관 *전에* 통과시켜 놓고 로직을 옮겼습니다. 이게 리팩토링의 정석입니다. 저는 "테스트는 나중에 추가하죠"로 시작해서 3주 뒤 유니크 제약 위반으로 코스 재등록이 전량 500 뜨는 사고를 본 적이 있습니다. 주인 구독의 soft delete 복원은 정확히 그 유형의 불변식인데, 이 PR 전까지 DB 레벨 검증이 0건이었습니다. 그 공백을 이관과 같은 커밋에서 메웠습니다.

둘째, **이관 메서드 시그니처를 `(Course course)`로 유지한 판단이 정확합니다.** `(courseId, ownerId)`로 "깔끔하게" 바꿨다면 `CourseSubscription.create(course, member)`를 위해 코스·멤버를 재조회하게 되어, 러닝 저장 트랜잭션에 쿼리 2개가 늘고 "동작 변화 0"이 깨집니다. 리팩토링에서 시그니처를 예쁘게 만들려다 성능·동작을 건드리는 건 흔한 함정인데 피했습니다.

셋째, **`RegionResolver`의 NEVER 계약에 테스트를 붙였습니다.** "어노테이션이 유실돼도 아무 테스트도 안 깨진다"는 걸 스스로 발견하고 `RegionResolverPropagationTest`로 메웠습니다. 주석은 실행되지 않지만 어노테이션과 테스트는 실행됩니다.

동작 불변 여부는 제가 diff로 직접 확인했고, 요청받은 4개 항목 전부 통과입니다 (아래 §검증 결과 표).

우려 사항은 두 가지입니다. **하나는 이 PR이 놓친 실제 회귀입니다** — `SentrySpanAspect`가 클래스 **이름 접미사**(`*Service`)로 포인트컷을 걸어 두었는데, 개명된 4개 클래스가 전부 그 포인트컷을 벗어났습니다. 기능은 안 깨지고 테스트 718개도 전부 그린이라 아무도 모르게 APM 스팬이 사라집니다. "동작 변화 0"의 유일한 예외이고, 하필 관측 쪽이라 터진 뒤에 알게 됩니다. 다른 하나는 **private → public 승격의 대가**입니다. `activate/deactivateOwnerSubscription`이 private 메서드에서 public 빈 API가 됐는데, "호출자 TX에 참여한다"는 계약을 강제하는 장치가 javadoc 문장 하나뿐입니다. 같은 패키지의 `CourseReadModelWriter`는 이미 `MANDATORY`로 그 계약을 실행 가능한 규칙으로 만들어 두었는데 말이죠.

---

## 총점: 89/100

## 등급: **A** — 약간의 개선 후 출시 가능

구조·동작 측면은 S급입니다. Sentry 포인트컷 회귀 1건과 트랜잭션 계약 강제 1건을 처리하면 그대로 S입니다.

---

## 차원별 점수

| 차원 | 점수 | 핵심 피드백 |
|------|------|-------------|
| 가독성 | 10/10 | 이름이 역할·트랜잭션 계약을 그대로 드러냄. `subscribeIfAbsent` ↔ `activateOwnerSubscription`의 대비 javadoc이 특히 좋다 |
| 아키텍처 준수 | 9/10 | course·running 두 도메인에서 repo를 보는 클래스는 Reader/Writer + 명시 예외 2건뿐 — 규칙 달성. 다만 "Writer = TX 경계"라는 어휘가 `CourseSubscriptionWriter`에서 흔들림 |
| 단일 책임 | 10/10 | 구독 테이블 쓰기가 두 곳 → 한 곳. `CourseWriter` 71줄 감소, 전 클래스 250줄 이하 |
| 캡슐화 | 8/10 | private 2개가 public 빈 API로 승격됐는데 TX 계약을 강제하는 런타임 장치가 없다 |
| 테스트 품질 | 9/10 | 이관 전 회귀 테스트 선행, 불변식 중심, 위임 횟수 나열 없음. 타우톨로지 1건 |
| 에러 처리 | 7/10 | 예외 타입·로그 문구는 그대로 이관됨(확인 완료). 그러나 Sentry 스팬 관측이 조용히 유실됨 |
| 성능 | 10/10 | 쿼리 증감 0. `(Course course)` 시그니처 유지로 재조회를 만들지 않음 |
| 보안 | 10/10 | 민감정보 신규 노출 없음. 응답 바디는 ErrorCode 기반이라 예외 메시지 변화가 API로 새지 않음(확인 완료) |
| 설계 일치도 | 8/10 | D1~D5 전부 구현 일치. 문서 §1의 "Reader는 트랜잭션을 열지 않는다"가 `RunningReader` 실제와 불일치, 미동기화 문서 1건 |
| 유지보수성 | 8/10 | 이름 기반 AOP 결합이 이번에 드러났는데 결합 자체는 그대로 — 다음 개명 때 또 조용히 깨진다 |

---

## 검증 결과 — "동작이 정말 안 바뀌었는가"

요청받은 4개 항목을 diff로 직접 확인했습니다.

| # | 검증 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | `CourseSubscriptionWriter`에 `@Transactional`이 새로 붙지 않았는가 | ✅ 통과 | `CourseSubscriptionWriter.java:38-39` — `@Service @RequiredArgsConstructor`뿐. 클래스·메서드 어디에도 `@Transactional` 없음 |
| 2 | `RegionResolver`의 `@Transactional(NEVER)` 생존 | ✅ 통과 | `RegionResolver.java:39` 유지. 게다가 `RegionResolverPropagationTest`로 실행 검증까지 신설 |
| 3 | `MemberRepository` → `MemberService.findMemberById` 교체로 예외 타입·조회 의미가 바뀌지 않았는가 | ✅ 통과 | 양쪽 모두 `memberRepository.findById(id)` 동일 호출. 예외도 `MemberNotFoundException` + `ErrorCode.MEMBER_NOT_FOUND` 동일. 메시지만 `"cannot find member id: {id}"`로 풍부해졌고, `GlobalExceptionAdvice:44`가 `createErrorResponse(e.getErrorCode())`로 **ErrorCode만** 사용하므로 HTTP 응답 바디는 완전 불변 |
| 4 | 이관된 `activate/deactivateOwnerSubscription`의 로직·로그가 원본과 동일한가 | ✅ 통과 | 분기 구조·순서·로그 레벨·로그 문구(`"Created new subscription for course={}, member={}"`, `"Restored subscription..."`, `"Unregistered subscription..."`) 전부 문자 단위 동일 |

추가로 제가 확인한 것:

- **호출 순서 불변** — `registerCourse`는 여전히 `구독 활성 → course.makePublic()` 순서 (`CourseWriter.java:151-152`). 원본과 같음.
- **프록시 초기화 불변** — `activateOwnerSubscription(course)`가 `course.getMember().getId()`를 호출하는데, 식별자 게터라 프록시를 초기화하지 않음. 원본 private 메서드와 동일한 접근.
- **레이어 규칙 달성** — course·running 두 도메인에서 `private final *Repository` 필드를 가진 클래스는 `CourseReader`, `CourseWriter`, `CourseSubscriptionWriter`, `CourseReadModelReader/Writer`, `CourseMapCacheEvictor`(예외 승인), `RegionResolver`(예외 승인), `RunningReader`, `RunningWriter` — Service/Facade는 0건. 규칙 §1 충족.
- **쓰기 TX가 Service/Facade로 새지 않음** — `CourseFacade`의 `@Transactional`은 4곳 전부 `readOnly = true`, `RunningCommandService`는 0건.
- **git 이력 보존** — 4건 모두 rename으로 검출됨(similarity 87~99%). `git log --follow` 추적 가능.
- **rename 파일 내용 순수성** — `CourseQueryServiceTest → CourseReaderTest`, `RegionServiceTest → RegionResolverTest` 전문 diff 결과 식별자 치환 외 변경 0건.

---

## 잘한 점

### 1. 이관 전에 회귀 테스트를 먼저 통과시킨 순서

**코드** (`src/test/java/soma/ghostrunner/domain/course/application/CourseWriterTest.java:150-178`)

```java
// when 3 - 재등록
courseWriter.updateCourse(id, new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

// then 3 - 복원되어 활성이고, 새 행을 만들지 않았다
assertThat(findOwnerSubscription(id).isActive()).isTrue();
assertThat(subscriptionRepository.findAll()).hasSize(1);
```

**왜 좋은가**: `assertThat(subscriptionRepository.findAll()).hasSize(1)` 이 한 줄이 핵심입니다. "복원이 아니라 새 행 삽입으로 바뀌었다"는 회귀는 단위 테스트(mock)로는 절대 안 잡힙니다. mock repository는 유니크 제약이 없으니까요. 실제 DB에서 행 개수를 세는 것만이 `uk_course_member` 위반을 사전에 잡습니다. 등록→해제→재등록 3단계를 한 테스트에 묶은 것도 적절합니다 — 이건 "상태 전이 사이클"이라는 하나의 불변식이지 3개의 시나리오가 아닙니다.

### 2. 규칙 차이를 통합하지 않고 대비 javadoc으로 못박은 판단

**코드** (`src/main/java/soma/ghostrunner/domain/course/application/CourseSubscriptionWriter.java:25-29`)

```java
* <p><b>두 진입점의 멱등 규칙이 다르다.</b> 해제된(soft delete) 구독을 만났을 때 러너 구독
* ({@link #subscribeIfAbsent})은 <b>그대로 둔다</b>(복원하지 않는다). 반면 주인 구독
* ({@link #activateOwnerSubscription})은 <b>복원한다</b> — ...
* 두 흐름의 조회·저장 모양이 닮았다고 하나로 합치면 이 규칙 차이가 플래그 뒤로 숨는다.
```

**왜 좋은가**: 두 메서드가 똑같이 `findByCourseIdAndMemberId`로 시작해서 `save`로 끝나기 때문에, DRY를 신봉하는 리뷰어라면 `subscribe(courseId, memberId, boolean restoreIfDeleted)` 같은 통합을 요구했을 겁니다. 그 순간 "왜 러너는 복원 안 하지?"라는 도메인 규칙이 boolean 파라미터 뒤로 사라집니다. 6개월 뒤 누가 호출부에서 `true`를 넘기면 러너 구독이 되살아나고, 탈퇴했던 코스가 내 목록에 다시 뜹니다. 형태의 중복과 규칙의 중복은 다르다는 걸 정확히 구분했습니다.

### 3. 단위 테스트를 "위임 검증"으로 축소한 재배치

**코드** (`src/test/java/soma/ghostrunner/domain/course/application/CourseWriterUnitTest.java:78-85`)

```java
* 여기서 지키는 계약은 두 가지다 —
* (1) 코스의 공개 상태 전환과 주인 구독 위임이 <b>함께</b> 일어난다,
* (2) 전환이 성립하지 않는 경우(검증 실패·이미 원하는 상태)에는 위임이 <b>일어나지 않는다</b>.
```

**왜 좋은가**: 구독 테이블 쓰기 규칙(생성/복원/유지)이 `CourseSubscriptionWriterUnitTest`로 옮겨간 뒤, `CourseWriterUnitTest`에 같은 검증을 남겨두면 규칙이 두 파일에 중복되어 한쪽만 고치는 사고가 납니다. 여기 남은 건 "언제 위임하고 언제 안 하는가"뿐이고, 그건 정확히 `CourseWriter`의 책임입니다. 특히 `publicizeWithoutName_failsBeforeTouchingSubscription`은 **검증이 부수효과보다 앞선다**는 순서 계약을 잡는 좋은 테스트입니다 — 이름 없는 코스를 공개하려다 실패했는데 구독만 살아나 있으면 "구독 중인데 지도에 안 보이는 코스"라는 유령 상태가 생깁니다.

### 4. 예외 승인 항목을 설계 문서에 미리 못박고 그대로 지킨 것

`CourseMapCacheEvictor`(커밋 후 콜백)와 `RegionResolver`(NEVER 계약)를 설계 §1·§4 D4에 예외로 명시하고, 구현에서도 건드리지 않았습니다. 리팩토링에서 가장 위험한 건 "일관성"을 이유로 특수 실행 문맥을 가진 컴포넌트까지 기계적으로 끌고 오는 것인데, 선을 먼저 긋고 지켰습니다. 다른 도메인(member·notice·device·auth)에 복제하지 않기로 한 §6도 같은 맥락에서 옳습니다.

---

## 개선 필요 사항

### [MAJOR-1] 클래스 개명으로 Sentry APM 스팬이 조용히 사라졌다 — "동작 변화 0"의 유일한 예외

**현재 코드** (`src/main/java/soma/ghostrunner/global/common/log/aspect/SentrySpanAspect.java:12-32`):

```java
/**
 * 메서드 별 실행 시간 캡처를 위해 메서드 앞뒤로 Sentry Span을 덧씌운다.
 * course와 running 패키지의 컨트롤러, 서비스, 리포지토리에 적용한다.
 **/
@Aspect
@Component
public class SentrySpanAspect {

    @Pointcut("execution(* soma.ghostrunner.domain.course..*Service.*(..)) || " +
            "execution(* soma.ghostrunner.domain.course..*Facade.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Service.*(..))")
    public void servicePackagePointCut() {}
```

**문제점**:

이 포인트컷은 **클래스 이름 접미사**로 대상을 고릅니다. 이번 PR에서 개명된 4개 클래스가 전부 `*Service`를 잃었습니다.

| 개명 전 (스팬 O) | 개명 후 (스팬 X) | 잃는 관측 |
|---|---|---|
| `CourseQueryService` | `CourseReader` | 주변 코스 검색(`findNearbyCourses`), 코스 단건 조회 |
| `RunningQueryService` | `RunningReader` | 러닝 조회 전체 — 고스트 페이징, 코스 통계, 월간 상태 |
| `CourseSubscriptionService` | `CourseSubscriptionWriter` | 러닝 저장 TX 안 구독 쓰기 |
| `RegionService` | `RegionResolver` | 지역 멱등 upsert (유니크 충돌 재시도 경로) |

여기에 직전 PR에서 만들어진 `CourseWriter`·`RunningWriter`·`CourseReadModelWriter/Reader`·`CourseMapCacheEvictor`까지 합치면, **이 리팩토링이 끝난 시점에 course·running 애플리케이션 계층에서 Sentry 스팬이 남아 있는 건 `CourseFacade`·`RunningCommandService`·`PathSimplificationService` 3개뿐**입니다.

컴파일도 되고 테스트 718개도 전부 그린입니다. **이 회귀를 잡을 수 있는 장치가 저장소에 하나도 없습니다.** 그래서 알게 되는 시점은 "지도 조회 p95가 튀어서 Sentry 트레이스를 열었는데, `CourseApi` 스팬 아래가 곧장 `Repository` 스팬으로 점프하고 그 사이 시간이 어디서 샜는지 안 보이는" 순간입니다. 리드모델 캐시 히트/미스 분기, 매퍼 변환 비용, 필터 조립 비용이 전부 "설명되지 않는 갭"으로 뭉칩니다. 저는 예전에 APM 계측이 리팩토링으로 절반 날아간 걸 두 달 뒤 장애 대응 중에 발견한 적이 있는데, 그때 원인 좁히는 데 걸린 시간이 계측이 살아 있었을 때의 몇 배였습니다.

부수적으로, 클래스 javadoc의 "**course와 running 패키지의 컨트롤러, 서비스, 리포지토리에 적용한다**"는 선언도 이제 사실이 아닙니다.

**개선 코드** (권장 — 이름이 아니라 위치·스테레오타입으로 건다):

```java
/**
 * course·running 도메인의 api/application/dao 계층 빈에 Sentry Span을 덧씌운다.
 *
 * <p><b>이름 접미사가 아니라 패키지로 거는 이유</b> — 과거 포인트컷은 {@code *Service} 접미사에
 * 의존해서, Reader/Writer 개명(docs/design/reader-writer-layering.md) 때 계측이 조용히 유실됐다.
 * 컴파일도 테스트도 이 유실을 잡지 못한다. 패키지 기준은 개명에 영향받지 않는다.
 */
@Pointcut("(execution(* soma.ghostrunner.domain.course.application..*.*(..)) || " +
          " execution(* soma.ghostrunner.domain.running.application..*.*(..))) && " +
          "@within(org.springframework.stereotype.Service) || " +
          "@within(org.springframework.stereotype.Component)")
public void applicationLayerPointCut() {}
```

스테레오타입 조합이 부담스럽다면, 최소 조치로 접미사만 보강해도 됩니다 (덜 권장 — 다음 개명에서 같은 사고가 반복됨):

```java
@Pointcut("execution(* soma.ghostrunner.domain.course..*Service.*(..)) || " +
          "execution(* soma.ghostrunner.domain.course..*Facade.*(..)) || " +
          "execution(* soma.ghostrunner.domain.course..*Reader.*(..)) || " +
          "execution(* soma.ghostrunner.domain.course..*Writer.*(..)) || " +
          "execution(* soma.ghostrunner.domain.course..*Resolver.*(..)) || " +
          "execution(* soma.ghostrunner.domain.running..*Service.*(..)) || " +
          "execution(* soma.ghostrunner.domain.running..*Reader.*(..)) || " +
          "execution(* soma.ghostrunner.domain.running..*Writer.*(..))")
public void servicePackagePointCut() {}
```

**개선 이유**: 포인트컷을 이름에 걸면 **AOP가 네이밍 컨벤션의 숨은 소비자**가 됩니다. 개명은 IDE가 안전하게 해주는 작업이라고 다들 믿는데, 문자열 안의 `*Service`는 IDE가 못 따라옵니다. 패키지·스테레오타입 기준은 "이 계층의 빈을 계측한다"는 원래 의도를 그대로 표현하면서 개명에 면역입니다. 참고로 스팬 이름은 `joinPoint.getSignature().getName()`(메서드명)이라 클래스명이 바뀌어도 대시보드 쿼리는 영향받지 않습니다 — 즉 되살리는 비용이 낮습니다.

**우선순위**: 이 PR과 같은 릴리스에 포함 권장. 설계 문서 §7의 "동작 변화 0" 문장에도 "단, Sentry 스팬 포인트컷은 별도 커밋으로 보정" 각주를 남기면 다음 사람이 헷갈리지 않습니다.

---

### [MAJOR-2] `CourseSubscriptionWriter`의 "호출자 TX 참여" 계약이 javadoc뿐 — 같은 패키지에 MANDATORY 선례가 있는데도

**현재 코드** (`src/main/java/soma/ghostrunner/domain/course/application/CourseSubscriptionWriter.java:31-43`):

```java
 * <p>자체 트랜잭션을 열지 않는다 — 호출자(CourseWriter / RunningWriter)의 트랜잭션에 참여한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSubscriptionWriter {

    private final CourseSubscriptionRepository subscriptionRepository;
    private final CourseRepository courseRepository;
    private final MemberService memberService;
```

**문제점**:

이번 PR에서 `activateOwnerSubscription` / `deactivateOwnerSubscription`은 **`CourseWriter`의 private 메서드에서 public 빈 메서드로 승격**됐습니다. 이전에는 "호출자 TX 안에서 실행된다"가 언어 차원에서 보장됐습니다 — private이니 `CourseWriter.updateCourse`(@Transactional) 밖에서는 부를 방법이 자체가 없었죠. 지금은 스프링 컨테이너에 등록된 빈의 public API이고, 그 계약을 지키는 건 javadoc 문장 하나입니다.

두 가지 시나리오가 걱정됩니다.

**시나리오 A — 트랜잭션 없는 호출자 등장.** 누군가 "코스 주인 구독만 되살리는 관리자 API"를 만들면서 `CourseFacade`나 `AdminApi`에서 `subscriptionWriter.activateOwnerSubscription(course)`를 직접 부릅니다. 컴파일 통과, 실행도 통과 — `SimpleJpaRepository.save()`가 자기 트랜잭션을 열기 때문에 "동작은 합니다". 그런데 `findByCourseIdAndMemberId` → `restore()` → `save()`가 각각 별개 트랜잭션이 되고, 이 흐름의 원자성이 사라집니다. 더 나쁜 건 `course` 엔티티가 detached라 `course.getMember()` 프록시 초기화에서 `LazyInitializationException`이 터질 수 있다는 겁니다. 이건 프로덕션 데이터로만 재현되는 유형입니다.

**시나리오 B — 선의의 어노테이션 추가.** 6개월 뒤 누가 "Writer인데 왜 `@Transactional`이 없지?"라며 클래스에 `@Transactional`을 붙입니다. 기본 REQUIRED라 지금은 아무것도 안 깨집니다. 그 다음 사람이 "구독 실패가 러닝 저장을 롤백시키면 안 되잖아"라며 `REQUIRES_NEW`로 바꿉니다. 이 순간 **코스 공개 전환은 롤백됐는데 주인 구독만 커밋되어 남는** 상태가 만들어집니다. 비공개 코스인데 구독은 활성 — 지도에는 안 뜨는데 내 코스 목록에는 뜨는 유령 데이터입니다. 어떤 테스트도 이걸 잡지 못합니다.

결정적으로, **같은 패키지에 이미 정답이 있습니다.** `RunningWriter.saveRun` 안에서 나란히 호출되는 두 줄을 보면:

```java
courseReadModelWriter.applyRun(running);                        // @Transactional(propagation = MANDATORY) — 강제됨
courseSubscriptionWriter.subscribeIfAbsent(courseId, member.getId());  // 동일한 계약, 강제 장치 없음
```

`CourseReadModelWriter.java:44`는 정확히 같은 계약("항상 호출자 TX 안")을 `@Transactional(propagation = Propagation.MANDATORY)`로 **실행 가능한 규칙**으로 만들어 두었습니다. `MemberVdotWriter.java:67`도 마찬가지고요. 한 호출부 안에서 같은 계약이 하나는 강제되고 하나는 안 되는 상태입니다.

**개선 코드**:

```java
/**
 * ...
 * <p><b>자체 쓰기 트랜잭션을 열지 않는다.</b> 항상 호출자(CourseWriter / RunningWriter)의 트랜잭션에
 * 참여한다 — 주인 구독 활성/해제는 코스 공개 전환과, 러너 구독 생성은 러닝 저장과 <b>같은 커밋 단위</b>여야
 * 하기 때문이다. 따로 커밋되면 "비공개인데 구독은 활성" 같은 유령 상태가 남는다.
 * MANDATORY는 이 계약을 주석이 아니라 실행되는 규칙으로 만든다.
 * (같은 계약의 선례: {@link CourseReadModelWriter}, {@code MemberVdotWriter#applyVdot})
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CourseSubscriptionWriter {
```

**개선 이유**:

1. **현재 호출자 2곳이 전부 `@Transactional` 안이므로 오늘의 동작은 그대로입니다.** `CourseWriter.updateCourse`(:71), `RunningWriter.saveRun`(:90) 모두 쓰기 TX를 엽니다. 즉 이 변경의 리스크가 사실상 0이고, 만약 전체 테스트가 깨진다면 그건 "TX 밖 호출자가 이미 있었다"는 **발견**이라 더 큰 소득입니다.
2. **실패 지점과 원인 지점이 붙습니다.** MANDATORY 위반은 진입 즉시 `IllegalTransactionStateException`으로 터집니다. 반면 지금 구조에서 시나리오 A는 며칠 뒤 데이터 정합성 문제로 나타나고, 그때는 원인 코드를 역추적해야 합니다. 이건 이 팀이 `RegionResolver`에 NEVER를 건 것과 **완전히 같은 논리**입니다 — 계약을 런타임 규칙으로 만들어 진입 즉시 실패시킨다.
3. **"동작 변화 0" 원칙과 충돌하지 않게** 별도 후속 커밋으로 분리하면 됩니다. 설계 §4 D2가 이미 "독립 호출을 막으려면 추후 MANDATORY 승격을 검토한다"고 적어 두었으니, 그 TODO를 지금 닫자는 제안입니다.

**대안 (MANDATORY가 부담스러울 경우)**: `RegionResolverPropagationTest`와 같은 형태로 전파 계약 테스트를 추가하세요. 다만 "TX 없이 호출하면 안 된다"는 negative 계약은 테스트로 표현하기가 애매해서, 어노테이션 쪽이 훨씬 저렴합니다.

---

### [MINOR-1] `CourseSubscriptionWriter`가 `CourseReader`를 두고 `CourseRepository`를 직접 본다

**현재 코드** (`src/main/java/soma/ghostrunner/domain/course/application/CourseSubscriptionWriter.java:62-69`):

```java
private void createSubscription(Long courseId, Long memberId) {
    Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, courseId));
    Member member = memberService.findMemberById(memberId);
    ...
}
```

**문제점**: 규칙 위반은 아닙니다 — Writer는 repo를 봐도 됩니다. 다만 이 5줄은 `CourseReader.findCourseById`(:35-38)와 **예외 타입·ErrorCode까지 완전히 동일한 중복**입니다. 재미있는 건 바로 다음 줄에서 멤버 조회는 `MemberService`를 경유한다는 점입니다(D3). "타 도메인은 서비스 경유, 자기 도메인은 repo 직접"이라는 기준 자체는 일관되지만, 결과적으로 `CourseRepository`의 소비자가 course 애플리케이션 계층 안에서 4개(`CourseReader`, `CourseWriter`, `CourseSubscriptionWriter`, `CourseReadModelWriter`)가 됩니다. 나중에 "코스 조회 시 soft delete 필터를 강화하자" 같은 변경이 오면 4곳을 다 찾아야 하고, 한 곳이 누락되면 삭제된 코스에 구독이 생기는 식의 어긋남이 생깁니다.

**개선 코드**:

```java
private final CourseSubscriptionRepository subscriptionRepository;
private final CourseReader courseReader;          // Writer → Reader 는 설계 §1에서 허용
private final MemberService memberService;

private void createSubscription(Long courseId, Long memberId) {
    Course course = courseReader.findCourseById(courseId);
    Member member = memberService.findMemberById(memberId);
    subscriptionRepository.save(CourseSubscription.create(course, member));
    log.info("Created new subscription: courseId={}, memberId={}", courseId, memberId);
}
```

**개선 이유**: 설계 §1이 `Writer ──► Reader`를 명시적으로 허용하고("트랜잭션 안 재조회. Reader는 호출자 TX에 참여"), `RunningWriter`가 이미 `CourseReader`·`RunningReader`를 그렇게 쓰고 있습니다. 예외 타입·쿼리가 동일하므로 **동작 변화 0**이고, "코스 한 건을 id로 로드하는 방법은 하나"라는 상태로 수렴합니다. 다만 이건 취향의 영역에 가까우니, 현행 유지를 택한다면 그것도 방어 가능합니다 — 결정만 남겨 두세요.

---

### [MINOR-2] "Reader는 트랜잭션을 열지 않는다"는 문서 선언이 `RunningReader`와 어긋난다

**현재 상태**:

| 클래스 | 실제 어노테이션 |
|---|---|
| `CourseReader` | 없음 (javadoc: "이 클래스는 트랜잭션을 열지 않는다") |
| `RunningReader` | `@Transactional(readOnly = true)` (클래스 레벨, `RunningReader.java:37`) |

그런데 설계 문서 §1 세 줄 요약 3번과 `docs/core/03-architecture.md`는 이렇게 적혀 있습니다:

> `@Transactional`(쓰기)은 Writer에만 존재한다. **Reader는 트랜잭션을 열지 않고**, 필요 시 호출자 트랜잭션에 참여한다.
> **`@Transactional`(쓰기)은 Writer에만 있다. 클래스 이름만으로 트랜잭션 경계를 판단할 수 있다.**

**문제점**: 괄호 안의 "(쓰기)"를 읽으면 정확한 문장이지만, 뒤따르는 "Reader는 트랜잭션을 열지 않고"와 "클래스 이름만으로 트랜잭션 경계를 판단할 수 있다"는 단정은 사실과 다릅니다. 같은 `*Reader` 이름을 가진 두 클래스의 트랜잭션 의미가 다르고, `CourseSubscriptionWriter`는 `*Writer`인데 TX를 안 엽니다.

이게 왜 위험하냐면 — **누군가 문서를 근거로 `RunningReader`의 `@Transactional(readOnly = true)`를 "규칙 위반"이라며 제거할 수 있습니다.** 그런데 `CourseFacade`의 `findPublicGhosts`(:167), `findTopRankingGhosts`(:178), `findTopPercentageGhosts`(:185), `findCourseStatistics`(:222)는 `@Transactional`이 없습니다. 이 경로들은 **`RunningReader`가 여는 readOnly 트랜잭션에 의존**하고 있고, 게다가 `application-dev.yml:23`·`application-local.yml:17`이 `open-in-view: false`입니다. 어노테이션을 떼는 순간 이 조회들은 트랜잭션 없이 실행되어 지연 로딩 시점에 `LazyInitializationException`으로 터집니다 — 그것도 고스트 목록/랭킹처럼 사용자가 자주 누르는 화면에서요.

**개선 제안**: 코드를 바꾸지 말고 **문서를 사실에 맞추세요**. 예:

> - **쓰기 `@Transactional`은 Writer에만 둔다.** Reader는 쓰기 트랜잭션을 열지 않는다.
> - Reader의 `readOnly = true`는 **허용**한다 (`RunningReader`). 여러 조회를 한 스냅샷으로 묶어야 하거나, 호출자(Facade/Api)가 트랜잭션을 열지 않는 조회 경로를 Reader가 스스로 감싸야 할 때 쓴다. `open-in-view: false`이므로 **떼면 지연 로딩이 깨진다.**
> - `CourseSubscriptionWriter`는 이름이 Writer지만 **자체 TX를 열지 않고 호출자 TX에 참여**한다. (→ MAJOR-2에서 MANDATORY 승격 시 이 각주도 갱신)

**개선 이유**: 아키텍처 문서의 단정문은 6개월 뒤 신규 입사자가 리팩토링의 근거로 삼는 문장입니다. "클래스 이름만으로 트랜잭션 경계를 판단할 수 있다"는 강한 약속이라, 예외가 두 건이나 있으면 그 예외를 문서가 직접 안고 가야 합니다. 그러지 않으면 문서를 믿은 사람이 프로덕션을 깹니다.

---

### [MINOR-3] 실패 전파 테스트 1건이 사실상 mock 자체를 검증한다

**현재 코드** (`src/test/java/soma/ghostrunner/domain/course/application/CourseSubscriptionWriterUnitTest.java:224-244`, `failsWithMemberNotFoundWhenMemberAbsent`):

```java
given(memberService.findMemberById(RUNNER_ID))
        .willThrow(new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));

assertThatThrownBy(() -> subscriptionWriter.subscribeIfAbsent(COURSE_ID, RUNNER_ID))
        .isInstanceOf(MemberNotFoundException.class)
        ...
then(subscriptionRepository).should(never()).save(any(CourseSubscription.class));
```

**문제점**: SUT에 예외를 잡거나 변환하는 코드가 없으므로, "던지면 전파된다"는 Java 언어 동작을 검증하는 셈입니다. 뒤의 `never()).save(...)`도 예외가 난 시점에 `save` 라인에 도달할 방법이 없어 항상 참입니다. D3의 진짜 위험은 "`MemberService.findMemberById`가 `memberRepository.findById`와 다르게 동작하지 않는가"인데, `memberService`를 mock으로 막으면 그 질문에는 답할 수 없습니다. (참고로 이 리뷰에서 코드로 직접 확인했고 동일합니다 — 위 검증 결과 표 #3.)

**개선 제안**: 삭제하거나, 정말 못박고 싶다면 통합 테스트로 올리세요 — 존재하지 않는 memberId로 `subscribeIfAbsent`를 부르면 `MEMBER_NOT_FOUND`가 나고 `course_subscription` 테이블에 행이 없다는 것을 실제 DB로 확인하는 형태입니다. 지금 러너 구독 경로는 **DB 레벨 검증이 0건**이라(주인 구독은 이번에 생겼습니다), 그쪽을 메우는 게 이 mock 테스트보다 훨씬 값어치가 있습니다. 다만 저장소 원칙(핵심 로직만)을 고려하면 "삭제"도 충분히 좋은 선택입니다.

---

### [MINOR-4] 미동기화 문서 1건

`docs/design/pacemaker-llm-flow.md:17`:

> `PacemakerApi`는 Command/Query 서비스를 직접 주입받는다 — Running 도메인(`RunningApi → RunningCommandService + RunningQueryService`)과 같은 컨벤션.

`RunningQueryService`는 이제 없습니다. 문서 동기화 커밋(`acc8a0e`)이 `course-cell-bucket-cache-design.md`·`05-cache-key-design.md`·`04-detailed-design.md`에는 개명 대응표를 붙였는데 이 파일은 누락됐습니다. 게다가 이 문장은 **"현재의 컨벤션"을 설명하는 살아 있는 서술**이라 이력 표기로 넘어가지 않습니다. `RunningReader`로 치환하면 됩니다.

(`docs/refactoring/course-read-model/core/01-current-state.md:105`의 `RunningQueryService` 언급은 "리팩토링 이전 현황" 스냅샷 문서이므로 그대로 두는 게 맞습니다.)

---

## 설계 문서 대비 차이

| 항목 | 설계 | 구현 | 판정 |
|------|------|------|------|
| D1 — `*QueryService` → `*Reader` | git mv로 개명, 호출자 7곳 + 테스트 기계적 치환 | `CourseReader`·`RunningReader`. rename 검출(similarity 87%/99%), 호출자 `RunningApi`·`RunningCommandService`·`RunningWriter`·`CourseFacade`·`PushEventListener`·`PacemakerValidator` 전부 치환 | ✅ 일치 |
| D2 — 구독 쓰기 단일화 | `CourseSubscriptionWriter`로 개명 + 주인 구독 이관, 자체 `@Transactional` 두지 않음 | 이관 완료, 로직·로그 문자 단위 동일. `CourseWriter`에서 `CourseSubscriptionRepository` 의존 제거 | ✅ 일치 |
| D2 후속 — MANDATORY 승격 | "독립 호출을 막으려면 추후 검토" | 미적용 | ⚠️ TODO 존치 (→ MAJOR-2) |
| D3 — `MemberRepository` → `MemberService` | 예외 타입·조회 의미 불변 확인 필요 | 동일 repo 메서드·동일 예외·동일 ErrorCode. 응답 바디 불변 | ✅ 일치 (검증 완료) |
| D4 — `RegionResolver` | 개명, `@Transactional(NEVER)` 유지 | 유지 + 전파 계약 테스트 신설 | ✅ 일치 (설계 이상) |
| D5 — 조회는 Reader 직접 호출 허용 | 현행 유지 | `RunningApi → RunningReader` 유지 | ✅ 일치 |
| §1 — repo는 Reader/Writer만 | Service/Facade의 repo 접근 0건 | course·running 두 도메인 0건 확인 | ✅ 일치 |
| §1 — "Reader는 트랜잭션을 열지 않는다" | 규칙 선언 | `RunningReader`는 `readOnly = true` 보유 | ⚠️ 문서 선언과 실제 불일치 (→ MINOR-2) |
| §5 — 동작 변화 0 | TX 경계·전파·예외 타입 불변 | TX/예외는 완전 불변. **Sentry 스팬 커버리지는 변화** | ⚠️ 미기재 부작용 (→ MAJOR-1) |
| §6 — 다른 도메인 미적용 | 의도된 범위 제한 | member·notice·device·auth 무변경 | ✅ 일치 |

---

## 선배 개발자의 한마디

**첫째, "테스트가 그린이니 동작 변화 0"이라는 등식을 한 단계 의심하세요.** 이번에 딱 그 틈으로 Sentry 스팬이 빠져나갔습니다. 컴파일러도 테스트도 못 잡는 결합이 저장소 어딘가에 늘 있습니다 — AOP 포인트컷, 로그 파싱 룰, 대시보드 쿼리, 알럿 조건, 문자열로 클래스명을 참조하는 설정. 이름을 바꾸는 PR을 낼 때 저는 항상 `grep -r "옛이름"`을 `.java`뿐 아니라 **`.xml`, `.yml`, `.md`, 그리고 문자열 리터럴 안까지** 훑습니다. 이번 PR에서 `grep`이 `SentrySpanAspect`의 `"*Service"` 리터럴에 닿지 않은 건, 그게 옛 클래스명이 아니라 **패턴**이었기 때문입니다. 그래서 규칙 하나 추가: **"이름 기반 설정을 쓰는 곳"의 목록을 저장소가 알고 있어야 합니다.** 지금은 `SentrySpanAspect` 하나뿐이니, 그 클래스 javadoc에 "이 포인트컷은 클래스 이름 접미사에 의존한다 — 개명 시 반드시 확인" 한 줄만 남겨도 다음 사람이 살아납니다.

**둘째, private을 public으로 승격할 때는 무엇을 잃었는지 세어 보세요.** `activateOwnerSubscription`이 private이던 시절, "호출자 트랜잭션 안에서만 실행된다"는 건 **증명된 사실**이었습니다. 컴파일러가 지켜줬으니까요. public 빈 메서드가 된 순간 그건 **기대**로 강등됐고, 기대는 6개월 뒤 팀원이 착실하게 배신합니다. 이 팀은 이미 `RegionResolver`의 NEVER, `CourseReadModelWriter`의 MANDATORY로 "계약을 런타임 규칙으로 만든다"는 좋은 습관을 갖고 있습니다. `CourseSubscriptionWriter`에도 같은 습관을 적용하세요 — 오늘은 아무것도 안 바뀌고, 미래의 사고 하나가 컴파일 직후 예외로 바뀝니다. 그게 새벽 3시 콜과 오후 3시 스택트레이스의 차이입니다.

마지막으로: 이 리팩토링의 방향 자체는 옳습니다. "Repository를 보는 건 Reader와 Writer뿐"이라는 한 문장 규칙은 신규 입사자가 첫날에 이해할 수 있고, 위반을 grep 한 번으로 찾을 수 있습니다. 좋은 규칙의 조건을 갖췄습니다. 다른 도메인에 복제하지 않기로 한 것도 잘한 절제고요. 위 두 건만 정리하면 그대로 나가도 좋습니다.

---

## 판정 결과 (Pragmatic Action Decision)

> 판정자: Pragmatic Action Decider
> 판정 기준: The Pragmatic Programmer (Andrew Hunt & David Thomas) 원칙 기반
> 작업 범위: `b319233..HEAD` — **순수 구조 리팩토링(동작 변화 0)**. 신규 기능 없음, TX 경계·전파·예외 타입·외부 API 전부 불변, 전체 빌드 그린(718 tests). 구현 커밋 `e11a88d` + 문서 동기화 커밋 `acc8a0e`.
> 판정 원칙: **"이번 변경이 만들어낸 회귀"만 이 PR의 책임이다.** 이전부터 있던 문제, 새 추상화 도입, 다른 도메인 확장은 전부 DEFER.

### 판정 요약

| 이슈 | 심각도 | 판정 | 근거 원칙 |
|------|--------|------|-----------|
| [MAJOR-1] Sentry APM 스팬 이탈 | MAJOR | 🔴 **FIX** (최소 수정만) | Tip #62 우연에 의한 프로그래밍 + Tip #8 Good Enough — **이번 PR이 직접 만든 회귀**. 단, 포인트컷 재설계는 DEFER |
| [MAJOR-2] MANDATORY 승격 | MAJOR | 🟡 **DEFER** | Tip #18 되돌림 가능 + Tip #36 완벽한 SW는 없다 — 설계 §4 D2가 명시적으로 미룬 항목, 오늘의 위험 0 |
| [MINOR-1] `CourseRepository` 직접 접근 | MINOR | 🟢 **PASS** | Tip #15 DRY(형태 중복 ≠ 규칙 중복) + Tip #36 — 규칙 위반 아님, 리팩토링 PR에서 의존 추가는 순변화 |
| [MINOR-2] 문서 단정문 ↔ `RunningReader` 불일치 | MINOR | 🔴 **FIX** (문서만) | Tip #5 깨진 유리창 + Tip #62 — **이 PR이 쓴 문장**이고, 그 문장을 믿으면 프로덕션이 깨짐 |
| [MINOR-3] 타우톨로지 테스트 | MINOR | 🔴 **FIX** (삭제만) | Tip #8 + 저장소 규약("테스트는 핵심 로직만") — **이 PR이 새로 쓴 테스트**. 통합 테스트 승격은 DEFER |
| [MINOR-4] `pacemaker-llm-flow.md` 미동기화 | MINOR | 🔴 **FIX** (1줄) | Tip #5 — `acc8a0e`의 산출물 누락. 이 PR이 스스로 선언한 범위 안 |

**FIX 4건 / DEFER 2건 / PASS 1건.** FIX 중 프로덕션 코드 수정은 MAJOR-1 하나뿐이고, 나머지 3건은 각각 문서 3줄·문서 1줄·테스트 삭제 20줄입니다. 총 diff 예상 +12/-25 수준.

#### 이미 확정된 설계 결정 — 재론 없이 🟢 PASS

아래 항목은 설계 문서에서 트레이드오프를 명시하고 채택한 결정이며, 리뷰에서도 "잘한 점"으로 평가됐습니다. 재검토 대상이 아닙니다.

| 결정 | PASS 근거 |
|---|---|
| `subscribeIfAbsent` ↔ `activateOwnerSubscription` 이름 비통일 | 멱등 규칙이 다름. Tip #17 — 다른 규칙에 같은 이름을 붙이는 것이 진짜 결합 |
| 두 경로의 `findByCourseIdAndMemberId` 중복 유지 | Tip #15 — 형태의 중복이지 지식의 중복이 아님. 통합하면 규칙 차이가 boolean 뒤로 숨음 |
| 이관 메서드 시그니처 `(Course course)` | Tip #8 — `(courseId, ownerId)`는 재조회 2회 유발, "동작 변화 0" 위반 |
| `RegionResolver` 개명 안 함 | `@Transactional(NEVER)` 계약과 Writer 어휘가 정면충돌. 설계 §4 D4 |
| 적용 범위가 running·course뿐 | 설계 §6의 의도된 절제. 전 도메인 복제는 Tip #42(Small Steps) 위반 |

---

### 수정 필수 항목 (FIX Tasks)

#### Task 1: [MAJOR-1] Sentry 포인트컷에 개명된 접미사 추가 — **최소 수정만**

- **판정**: 🔴 FIX
- **근거 원칙**: Tip #62 *"Don't Program by Coincidence. Rely only on reliable things."* — 계측이 클래스 이름 접미사라는 **우연한 규약**에 의존하고 있었고, 이번 개명이 그 우연을 깼습니다. 동시에 Tip #8 *"Make Quality a Requirements Issue"* — 이 PR의 요구사항이 "동작 변화 0"인데 관측 커버리지가 실제로 변했으므로, 요구사항 미달 상태입니다. 테스트도 컴파일러도 못 잡고, 알게 되는 시점이 장애 대응 중이라는 점에서 Tip #38(Crash Early)의 정반대 — **조용히 실패하는 코드**입니다.
- **왜 "글로벌 패키지라 범위 밖"이 면죄부가 아닌가**: 범위를 벗어난 건 맞습니다. 그러나 이 회귀를 **인지한 사람은 지금 이 PR의 작성자뿐**이고, 원인(개명)과 결과(스팬 소실)를 아는 사람이 고치지 않으면 6개월 뒤 다른 사람이 "왜 여기 스팬이 없지?"부터 시작해야 합니다. 리팩토링의 부산물을 그 리팩토링이 치우는 건 범위 확대가 아니라 **범위의 완결**입니다.
- **수정 대상**: `src/main/java/soma/ghostrunner/global/common/log/aspect/SentrySpanAspect.java:12-28`
- **수정 내용**:
  1. `servicePackagePointCut()`에 `*Reader` / `*Writer` / `*Resolver` 접미사를 course·running 양쪽에 추가. 리포트의 "덜 권장" 안(라인 179-187)을 그대로 채택합니다.
  2. 클래스 javadoc(:15)의 "컨트롤러, 서비스, 리포지토리에 적용한다"를 실제에 맞게 수정하고, **경고 한 줄을 명시**: `이 포인트컷은 클래스 이름 접미사에 의존한다 — 개명 시 반드시 이 파일을 함께 확인할 것. (사례: docs/design/reader-writer-layering.md 개명으로 스팬 4건 유실)`
- **⚠️ 리포트의 "권장" 안(패키지+스테레오타입, 라인 169-173)은 그대로 채택하지 말 것**:
  ```java
  "(execution(...course.application..*.*(..)) || execution(...running.application..*.*(..))) && " +
  "@within(org.springframework.stereotype.Service) || @within(org.springframework.stereotype.Component)"
  ```
  AspectJ에서 `&&`는 `||`보다 결합이 강하므로 이 식은 `((A||B) && @within(Service)) || @within(Component)`로 파싱됩니다. 즉 **저장소 전체의 모든 `@Component` 빈**(필터, 클라이언트, config, 리스너 전부)이 계측 대상이 됩니다. 관측 회귀를 고치려다 전 애플리케이션에 AOP 프록시를 씌우는 변경이 되고, 이건 "동작 변화 0" PR에 절대 들어갈 수 없습니다. 괄호를 고쳐 쓰더라도 대상 빈 집합이 크게 바뀌므로 **별도 티켓(DEFER-2)** 사안입니다.
- **`*Writer`를 함께 넣는 이유**: `CourseWriter`·`RunningWriter`·`CourseReadModelWriter/Reader`는 직전 PR(`e95ce70`)에서 유실된 것이라 엄밀히는 이 PR의 책임이 아닙니다. 그러나 **같은 문자열 한 곳을 편집하는 동일 작업**이므로 티켓을 쪼개는 것이 순수 관리 비용입니다. Tip #42(Small Steps)는 "작은 단계"를 말하지 "인위적 분할"을 말하지 않습니다.
- **`CourseMapCacheEvictor`(`*Evictor`)는 넣지 말 것**: AFTER_COMMIT 콜백이라 부모 스팬 컨텍스트 밖에서 실행될 수 있고, 이번 개명 대상도 아닙니다. DEFER-2에서 판단.
- **예상 작업량**: **S** (포인트컷 6줄 + javadoc 3줄)
- **검증 방법**:
  1. `./gradlew build` 그린 (718 tests 유지 — 이 변경으로 깨지는 테스트가 있으면 안 됨)
  2. 로컬 부팅 후 `((Advised) applicationContext.getBean(CourseReader.class)).getAdvisors()`에 `SentrySpanAspect` 어드바이저가 포함되는지 확인. `CourseReader`는 `@Transactional`이 없어(확인 완료) 프록시화 자체가 이 aspect 매칭의 증거가 됩니다.
  3. dev 배포 후 Sentry 트레이스에서 `findNearbyCourses` / `findCourseById` 스팬 복귀 확인.

---

#### Task 2: [MINOR-2] 아키텍처 문서의 단정문을 사실에 맞춘다 — **문서만, 코드 무변경**

- **판정**: 🔴 FIX
- **근거 원칙**: Tip #5 *"Don't Live with Broken Windows."* — 아키텍처 문서의 단정문은 6개월 뒤 신규 입사자가 리팩토링의 **근거로 인용하는 문장**입니다. 사실이 아닌 규칙은 그 자체가 깨진 유리창이고, 전파 범위가 저장소 전체입니다. 여기에 Tip #62 — 현재 고스트 목록/랭킹 조회의 정상 동작이 `RunningReader`의 `readOnly=true`라는 **문서화되지 않은 가정**에 의존하고 있습니다.
- **왜 "기존 문제"가 아니라 이 PR의 책임인가**: 이 두 문장은 **이 PR의 리뷰 범위 안에 있는 커밋(`acc8a0e`)이 작성/수정한 문장**입니다. 즉 이번 PR이 만든 문서 부채입니다.
- **위험이 가설이 아님 (직접 확인)**: `CourseFacade.findPublicGhosts`(:167), `findTopRankingGhosts`(:178), `findTopPercentageGhosts`(:185), `findCourseStatistics`(:222) 네 곳 모두 `@Transactional`이 **없습니다**(:56/:147/:171/:190은 각각 다른 메서드에 붙어 있음). `open-in-view: false`(`application-dev.yml:23`, `application-local.yml:17`)이므로, 문서를 근거로 `RunningReader.java:37`의 `@Transactional(readOnly = true)`를 떼면 이 네 경로가 지연 로딩 시점에 `LazyInitializationException`으로 터집니다.
- **수정 대상**:
  - `docs/design/reader-writer-layering.md:38`
  - `docs/core/03-architecture.md:66`
- **수정 내용**: "Reader는 트랜잭션을 열지 않는다" / "클래스 이름만으로 트랜잭션 경계를 판단할 수 있다"는 단정을 **예외 2건을 안고 가는 서술**로 교체.
  1. 쓰기 `@Transactional`은 Writer에만 둔다 (이 규칙은 유지).
  2. Reader의 `readOnly = true`는 **허용**한다 — `RunningReader`. 호출자(Facade/Api)가 트랜잭션을 열지 않는 조회 경로를 Reader가 스스로 감싸야 하기 때문. **`open-in-view: false`이므로 떼면 지연 로딩이 깨진다** (해당 경로: `CourseFacade`의 고스트 페이징·TOP랭킹·상위 퍼센트·코스 통계).
  3. `CourseSubscriptionWriter`는 이름이 Writer지만 **자체 TX를 열지 않고 호출자 TX에 참여**한다. (→ DEFER-1 MANDATORY 승격 시 갱신)
- **`RunningReader`의 어노테이션은 절대 건드리지 말 것.** 이건 코드를 문서에 맞추는 게 아니라 문서를 코드에 맞추는 작업입니다.
- **예상 작업량**: **S** (문서 2파일, 각 3~4줄)
- **검증 방법**: 문서 변경이므로 빌드 영향 없음. 리뷰어가 문서를 읽고 `RunningReader.java:37`·`CourseSubscriptionWriter.java:36-39`와 대조해 모순이 없는지 확인.

---

#### Task 3: [MINOR-3] 타우톨로지 테스트 삭제 — **삭제만, 통합 테스트 추가는 하지 않음**

- **판정**: 🔴 FIX
- **근거 원칙**: Tip #8 *"Good Enough software is not sloppy software"* + 저장소 규약("테스트는 핵심 로직만 — 망라식 지양"). 이 테스트는 **이번 PR이 새로 작성한 코드**이므로 원칙 3(현재 작업에서 새로 쓴 코드는 내가 책임진다)에 정면으로 걸립니다. `memberService`를 mock으로 막고 "던지면 전파된다"를 검증하는 건 Java 언어 동작을 테스트하는 것이고, 뒤의 `never()).save(...)`는 도달 불가능한 라인에 대한 단언이라 **항상 참**입니다.
- **진짜 비용은 유지보수가 아니라 오해**: 이 테스트가 있으면 러너 구독 경로에 D3(MemberRepository→MemberService 교체) 검증이 있는 것처럼 보입니다. 실제로는 mock이 그 질문을 통째로 가리고 있어 **검증 커버리지 0인데 0이 아닌 것처럼 보이는** 상태입니다. 그게 없는 것보다 나쁩니다.
- **수정 대상**: `src/test/java/soma/ghostrunner/domain/course/application/CourseSubscriptionWriterUnitTest.java:224-244` (`failsWithMemberNotFoundWhenMemberAbsent`)
- **수정 내용**: 해당 테스트 메서드 삭제. 사용되지 않게 되는 import(`MemberNotFoundException` 등)가 있으면 함께 정리.
- **통합 테스트로 승격하지 말 것 (→ DEFER-3)**: 러너 구독 경로의 DB 레벨 검증 공백은 실재하지만, 그건 **이번 PR이 만든 공백이 아니라 원래 있던 공백**입니다. 순수 리팩토링 PR에 새 통합 테스트를 얹으면 리뷰 범위가 흐려지고, 통합 테스트는 컨테이너 기동 비용이 있어 "덤으로 넣는" 성격의 변경이 아닙니다.
- **솔직한 평가**: 이 항목은 4개 FIX 중 우선순위가 가장 낮습니다. DEFER해도 프로덕션에 아무 영향이 없습니다. 그럼에도 FIX로 두는 이유는 **삭제 비용이 음수**이기 때문입니다 — 티켓을 만들고 백로그에서 관리하는 비용이 지금 20줄 지우는 비용보다 큽니다. 일정이 극도로 촉박하면 이 하나만 DEFER로 내려도 반대하지 않겠습니다.
- **예상 작업량**: **S** (-20줄)
- **검증 방법**: `./gradlew test --tests "soma.ghostrunner.domain.course.application.CourseSubscriptionWriterUnitTest"` 통과, 전체 테스트 수 718 → 717.

---

#### Task 4: [MINOR-4] `pacemaker-llm-flow.md`의 죽은 클래스명 치환

- **판정**: 🔴 FIX
- **근거 원칙**: Tip #5 깨진 유리창. 문서 동기화 커밋(`acc8a0e`)이 **이 PR의 명시적 산출물**인데 한 파일이 누락된 상태입니다. "문서 동기화 커밋이 있는데도 옛 이름이 남아 있다"는 상태가 다음 사람에게 주는 신호는 "이 저장소의 문서 동기화는 신뢰할 수 없다"입니다. 그 신호가 퍼지면 아무도 문서를 갱신하지 않게 됩니다.
- **수정 대상**: `docs/design/pacemaker-llm-flow.md:17`
- **수정 내용**: `RunningApi → RunningCommandService + RunningQueryService` → `RunningApi → RunningCommandService + RunningReader`. 이 문장은 이력 서술이 아니라 **"현재의 컨벤션"을 설명하는 살아 있는 문장**이므로 대응표 각주가 아니라 본문 치환이 맞습니다.
- **함께 확인할 것**: `docs/refactoring/course-read-model/core/01-current-state.md:105`의 `RunningQueryService`는 "리팩토링 이전 현황" 스냅샷이므로 **그대로 둡니다**. 스냅샷 문서를 현재형으로 고치면 그 문서의 존재 이유가 사라집니다.
- **예상 작업량**: **S** (1줄)
- **검증 방법**: `grep -rn "RunningQueryService\|CourseQueryService\|CourseSubscriptionService\|RegionService" docs/` 결과가 (a) `01-current-state.md`의 스냅샷, (b) `reader-writer-layering.md`의 개명 대응표, (c) 각 문서의 이력 각주, (d) 이 리뷰 문서 — 네 종류만 남는지 확인.

---

### 별도 티켓 권장 항목 (DEFER)

#### DEFER-1: `CourseSubscriptionWriter`에 `@Transactional(propagation = MANDATORY)` 승격 — [MAJOR-2]

- **근거 원칙**: Tip #18 *"There Are No Final Decisions"* + Tip #36 *"You Can't Write Perfect Software"*
- **왜 지금이 아닌가 — 세 가지**:
  1. **설계 §4 D2(`reader-writer-layering.md:130-131`)가 "자체 `@Transactional`은 두지 않는다(현행 유지) — 독립 호출을 막으려면 추후 `MANDATORY` 승격을 검토한다"고 명시적으로 미뤄둔 항목입니다.** 설계 리뷰를 거쳐 의도적으로 미룬 것을 구현 리뷰에서 당기는 건, 설계 문서를 구속력 없는 문서로 만드는 일입니다. 그 순간 "설계에 뭘 써두든 어차피 리뷰에서 뒤집힌다"가 되고, 이건 코드보다 더 비싼 깨진 유리창입니다.
  2. **엄밀히 동작 변화입니다.** MANDATORY는 TX 없는 호출 시 `IllegalTransactionStateException`을 던집니다. 이 PR의 단 하나의 계약이 "동작 변화 0"인데, 그 계약에 예외를 하나 더 만들면 **"이 PR은 동작을 안 바꿨다"는 문장이 두 개의 각주를 달게 됩니다.** 각주가 두 개 달린 계약은 계약이 아닙니다. MAJOR-1은 회귀 복구(원래 상태로 되돌림)라 각주가 필요 없지만, MANDATORY는 새 규칙 도입입니다.
  3. **오늘의 위험이 0입니다.** 호출자는 `CourseWriter.java:151,159` 두 곳뿐이고(직접 확인), 둘 다 `updateCourse`의 쓰기 TX 안입니다. 리포트가 우려하는 시나리오 A/B는 전부 **미래의 호출자**를 전제합니다.
- **되돌림 가능성 판정**: 어노테이션 1줄 추가입니다. DB 스키마도, 외부 API 계약도, 공개 인터페이스 시그니처도 아닙니다. **지금 하는 비용과 다음 PR에서 하는 비용이 동일합니다.** Tip #18의 기준으로 지연 비용이 0인 항목은 DEFER가 정답입니다.
- **다만 우선순위는 높게**: 리포트의 지적 자체는 옳습니다. private → public 승격으로 "컴파일러가 보장하던 사실"이 "javadoc이 기대하는 바"로 강등된 건 실제 손실입니다. **다음 PR에서 처리**하고, 그때는 아래를 함께 합니다.
  - `CourseSubscriptionWriter`에 `@Transactional(propagation = Propagation.MANDATORY)`
  - `RegionResolverPropagationTest`와 같은 형태의 전파 계약 테스트 (TX 없이 호출 → `IllegalTransactionStateException`)
  - 설계 문서 §4 D2의 "추후 검토" 문장을 "적용 완료"로 갱신
  - Task 2에서 문서에 적은 "`CourseSubscriptionWriter`는 Writer지만 자체 TX를 열지 않는다" 각주 갱신
- **선행 조건**: 전체 테스트가 그린이어야 합니다. 만약 깨진다면 "TX 밖 호출자가 이미 있었다"는 발견이므로 그 자체가 소득입니다. 이 확인은 별도 PR에서 여유 있게 해야지, 리팩토링 PR 막판에 할 일이 아닙니다.
- **예상 작업량**: S (어노테이션 1줄 + 테스트 1개 + 문서 2줄)

#### DEFER-2: `SentrySpanAspect` 포인트컷을 이름 기반 → 패키지/스테레오타입 기반으로 재설계

- **근거 원칙**: Tip #17 *"Eliminate Effects Between Unrelated Things"* — 네이밍 컨벤션과 APM 계측이 결합되어 있고, Task 1은 그 결합을 **그대로 둔 채 증상만 복구**합니다. 다음 개명에서 같은 사고가 반복됩니다.
- **왜 지금이 아닌가**: Task 1(접미사 추가)은 유실된 계측을 원상 복구하는 변경이라 대상 빈 집합이 개명 전과 동일합니다. 반면 포인트컷 재설계는 **계측 대상 집합 자체를 바꾸는 변경**이고(스팬 개수·트레이스 볼륨·Sentry 쿼터에 영향), "동작 변화 0" PR에 들어갈 수 없습니다. 리포트의 제안 코드에 연산자 우선순위 버그가 있다는 점(Task 1 참조)도 이 작업이 **신중한 검토를 요하는 독립 작업**임을 보여줍니다.
- **티켓 내용**:
  - 포인트컷을 `soma.ghostrunner.domain.{course,running}.application..*` 패키지 기준으로 전환. 괄호를 명시해 `&&`/`||` 우선순위 버그를 피할 것.
  - 대상 집합 변화(before/after 빈 목록)를 PR 설명에 첨부.
  - `CourseMapCacheEvictor`(AFTER_COMMIT 콜백) 포함 여부 결정 — 부모 스팬 컨텍스트 밖일 가능성 검토.
  - dto/support 하위 패키지 제외 여부 결정.
  - 가능하면 "핵심 빈이 aspect 어드바이저를 갖는가"를 검사하는 아키텍처 테스트 1건 추가 → 다음 개명 때 **컴파일 타임에 가까운 시점에** 실패하게 만듦.
- **예상 작업량**: M

#### DEFER-3: 러너 구독 경로(`subscribeIfAbsent`)의 DB 레벨 통합 테스트 보강

- **근거 원칙**: Tip #8 Good Enough — **이번 PR이 만든 공백이 아니라 원래 있던 공백**입니다. 이 PR은 오히려 주인 구독 쪽 DB 검증을 0건 → 1건으로 **늘렸습니다**(`CourseWriterTest.ownerSubscription_isRestoredNotDuplicated_onReRegister`). 개선한 PR에 "나머지도 마저 해라"를 요구하면 아무도 부분 개선을 하지 않게 됩니다.
- **티켓 내용**: 존재하지 않는 `memberId`로 `subscribeIfAbsent` 호출 시 `MEMBER_NOT_FOUND`가 발생하고 `course_subscription` 테이블에 행이 생기지 않음을 실제 DB로 검증. 더불어 "해제된 러너 구독은 복원하지 않는다"는 멱등 규칙(`CourseSubscriptionWriter.java:46-48`의 핵심 계약)도 DB 레벨로 잡을 것 — 이쪽이 예외 전파보다 훨씬 값어치 있습니다.
- **DEFER-1과 묶어서 처리하면 효율적**입니다(둘 다 `CourseSubscriptionWriter` 대상).
- **예상 작업량**: S~M

#### DEFER-4 (신규 제안): "이름 기반 설정"의 목록을 저장소가 알게 하기

- **근거 원칙**: Tip #62. 리포트 "선배 개발자의 한마디" 첫 문단을 티켓으로 만든 것입니다.
- **왜 별도 티켓인가**: Task 1에서 `SentrySpanAspect` javadoc에 경고 한 줄을 남기는 것으로 **당장의 지뢰는 표시**됩니다. 저장소 전역의 문자열 기반 결합(AOP 포인트컷, 로그 파싱 룰, 대시보드 쿼리, 알럿 조건, yml 내 클래스명 참조)을 전수 조사하는 건 별개의 조사 작업입니다.
- **티켓 내용**: `docs/core/`에 "개명 시 확인 목록" 섹션 신설. 현재 알려진 항목은 `SentrySpanAspect` 1건. 이후 발견될 때마다 추가.
- **예상 작업량**: S (조사 M)

---

### 넘어가도 되는 항목 (PASS)

#### [MINOR-1] `CourseSubscriptionWriter`가 `CourseReader` 대신 `CourseRepository`를 직접 본다 — 🟢 PASS, 현행 유지

- **근거 원칙**: Tip #15 DRY — 중복된 것은 **비즈니스 규칙이 아니라 조회 보일러플레이트**입니다. `findById + orElseThrow(CourseNotFoundException)`는 지식이 아니라 형태입니다. 리포트가 우려하는 "soft delete 필터 강화 시 4곳 수정"은 실제로는 그 시점에 `CourseRepository`의 쿼리 메서드나 `@Where`/`@SQLRestriction` 한 곳에서 처리될 사안이지, 호출부 4곳을 개별 수정할 일이 아닙니다.
- **규칙 위반이 아님**: 설계 §1이 "Writer는 자기 도메인 repo를 본다"를 명시적으로 허용합니다. 위반이 아닌 것을 고치는 건 Tip #36 — 완벽주의입니다. 리포트 작성자도 "취향의 영역"이라고 스스로 인정했습니다.
- **오히려 순변화(net-negative)**: `CourseSubscriptionWriter → CourseReader` 의존을 추가하면 course 애플리케이션 계층의 클래스 간 엣지가 하나 늘고, 단위 테스트의 mock이 하나 늘어납니다. "동작 변화 0" 리팩토링 PR에서 얻는 것(중복 3줄 제거) 대비 잃는 것(의존 엣지 1개 + 테스트 수정)이 큽니다.
- **결정 기록**: "코스 자기 도메인 조회는 Writer가 repo를 직접 본다. Reader 경유는 재조회·의존 추가가 정당화될 때만." — 이 한 줄을 남겨서 다음 리뷰에서 같은 논의가 반복되지 않게 하십시오. 다만 이건 별도 문서 작업이 아니라 이 리뷰 문서로 충분합니다.

---

### 실용주의 프로그래머의 한마디

이 PR에 대한 판정은 **"자기가 깬 것만 치우고 나가라"** 한 문장으로 요약됩니다.

리팩토링 PR의 리뷰에서 가장 흔한 실패는 두 가지고, 방향이 정반대입니다. 하나는 "테스트 그린이니 끝"이라며 컴파일러가 못 보는 결합을 놓고 나가는 것 — 이번엔 Sentry 스팬 4개가 정확히 그 틈으로 빠져나갔고, 그래서 **FIX**입니다. 관측 유실은 나중에 고치는 비용이 지금의 몇 배가 아니라, **나중에 고칠 기회 자체가 장애 한복판에서만 온다**는 게 문제입니다.

다른 하나는 리뷰어가 "이왕 손댄 김에"를 붙이는 것입니다. MANDATORY 승격은 리뷰어가 100% 옳습니다. private에서 public 빈 API로 올라간 순간 컴파일러의 보장이 javadoc의 기대로 강등된 건 실제 손실이고, 이 팀은 `RegionResolver`의 NEVER와 `CourseReadModelWriter`의 MANDATORY로 이미 정답을 알고 있습니다. 그럼에도 **DEFER**인 이유는 두 가지입니다. 설계 문서가 명시적으로 미뤄둔 항목을 구현 리뷰에서 당기면 설계 문서가 구속력을 잃고, MANDATORY는 엄밀히 동작 변화라 이 PR의 유일한 계약("동작 변화 0")에 각주를 하나 더 답니다. 각주 두 개짜리 계약은 계약이 아닙니다. 그리고 결정적으로 — **어노테이션 한 줄은 지금 붙이나 다음 주에 붙이나 비용이 같습니다.** Tip #18이 말하는 되돌림 가능성이 정확히 이 뜻입니다. 지연 비용이 0인 개선은 미뤄도 됩니다. 지연 비용이 0이 아닌 회귀만 지금 고칩니다.

FIX 4건 중 3건이 문서 3줄·문서 1줄·테스트 20줄 삭제인 것도 의도적입니다. 완벽한 코드는 없습니다(Tip #36). 하지만 **자기가 쓴 문장이 거짓인 채로 머지되는 것**은 다른 종류의 문제입니다. `RunningReader`의 `readOnly=true`를 문서 근거로 떼면 고스트 목록이 터진다는 걸, 그 문서를 쓴 이 PR 말고 누가 압니까.

> **"자기가 깬 유리창은 자기가 갈아 끼운다. 원래 금 가 있던 창은 티켓으로 남긴다."**
