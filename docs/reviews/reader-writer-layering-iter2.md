# 코드 품질 리포트 — iter1 FIX 반영분 검증 (iter2)

- **리뷰 성격**: **전체 재감사 아님.** iter1(89/100, A)에서 FIX 판정된 4건이 올바르게 반영됐는지 + 그 반영이 새 문제를 만들지 않았는지만 검증한다. iter1에서 PASS/DEFER로 판정된 항목은 재론하지 않는다.
- **검증 대상**: `git diff acc8a0e..HEAD` — 커밋 `585b870` (5 files / +28 / -32, 리뷰 문서 제외)
- **iter1 보고서**: `docs/reviews/reader-writer-layering-iter1.md`
- **리뷰 일자**: 2026-08-09

---

## 총평

4건 전부 **의도한 대로 반영됐고, 반영 방식이 iter1 판정문의 지시와 정확히 일치**합니다. 특히 두 가지가 인상적입니다.

첫째, **iter1 보고서가 "권장"으로 제시했던 포인트컷 재설계 코드를 거절하고 "덜 권장" 안을 채택한 판단이 옳습니다.** 판정문이 지적했듯 권장안에는 AspectJ 연산자 결합 버그(`(A||B) && @within(Service) || @within(Component)`)가 있어 저장소 전체의 `@Component`가 계측 대상이 될 뻔했습니다. 리뷰어의 코드를 그대로 붙여넣지 않고 판정 근거를 읽고 판단한 흔적입니다. 그리고 **채택된 코드에는 그 종류의 문제가 없습니다** — 새 포인트컷은 `execution(...)` 8개의 순수 `||` 나열이라 `&&`가 아예 등장하지 않고, 따라서 결합 우선순위 논쟁 자체가 성립하지 않습니다.

둘째, **`MemberVdotWriter`를 넣지 않은 판단이 맞습니다.** 이건 뒤에서 근거와 함께 확인합니다.

문제는 **문서 3건 중 2건에서 사실 오류·누락이 남았다**는 점입니다. 심각도는 전부 MINOR이고 전부 문서이며, 프로덕션 코드는 손댈 것이 없습니다. 다만 이번 FIX의 절반이 "문서를 사실에 맞추는 작업"이었다는 점을 생각하면, 그 작업 자체가 새 사실 오류를 남긴 건 짚고 가야 합니다.

---

## 총점: 94/100

## 등급: **S** — 출시 준비 완료

프로덕션 코드(`SentrySpanAspect`) 변경은 **그대로 나가도 좋습니다.** 남은 2건은 문서 사실관계 정정이라 릴리스를 막지 않습니다.

---

## 차원별 점수 (이번 diff 범위 한정)

| 차원 | 점수 | 핵심 피드백 |
|------|------|-------------|
| 가독성 | 10/10 | 포인트컷 8줄이 도메인·접미사 순으로 정렬돼 한눈에 집합이 읽힌다. javadoc 경고문이 "왜 위험한지 + 실제 사례"까지 담아 다음 사람이 행동할 수 있다 |
| 아키텍처 준수 | 10/10 | 포인트컷 스코프가 `course..`/`running..`로 유지됨. 새 도메인을 끌어들이지 않았다 |
| 단일 책임 | 10/10 | 해당 없음(변경 없음) |
| 캡슐화 | 10/10 | 해당 없음(변경 없음) |
| 테스트 품질 | 10/10 | 타우톨로지 1건 삭제, 남은 7개 시나리오가 전부 멱등·soft delete 불변식. 미사용 import 3개까지 정리됨 |
| 에러 처리 | 10/10 | iter1 MAJOR-1(관측 유실)이 실제로 복구됐고, 재발 방지 경고까지 코드에 남았다 |
| 성능 | 9/10 | 신규 advised 클래스 8개 전부 프록시 가능(final 없음). 스팬 볼륨 증가는 요청당 한 자릿수 수준. `CourseFacade:196` 루프 안 호출만 페이지 크기에 비례 |
| 보안 | 10/10 | 스팬 이름은 메서드명뿐 — 인자·PII 미포함 |
| 설계 일치도 | 7/10 | `reader-writer-layering.md:196`이 삭제된 테스트를 반영하지 않아 **718 tests**로 남음(실제 717). Sentry 계측 변경이 §7에 전혀 기록되지 않음 |
| 유지보수성 | 8/10 | 경고 javadoc은 좋은 안전장치. 다만 TX 예외 목록이 실제 3건인데 문서는 "예외 2건"으로 단정 |

---

## FIX 4건 검증 결과

| # | FIX 항목 | 판정 | 근거 |
|---|---|---|---|
| FIX-1 | Sentry 포인트컷 5줄 추가 + javadoc 경고 | ✅ **통과** | 매칭 집합 전수 확인 완료(아래 §1). 연산자 버그 없음, 우발 매칭 0건, 기존 매칭 유실 0건, 프록시 불가 타입 0건 |
| FIX-2 | 문서의 "Reader는 TX를 열지 않는다" 정정 | ⚠️ **조건부 통과** | 정정 자체는 코드와 일치. `RunningReader`의 `readOnly` 생존·`CourseSubscriptionWriter`의 무TX 확인. 다만 예외 목록에 3번째 사례 누락(→ RESIDUAL-2) |
| FIX-3 | 타우톨로지 테스트 삭제 | ✅ **통과** | 삭제 + 미사용 import 3개 정리. 커버리지 공백 없음(아래 §3) |
| FIX-4 | `pacemaker-llm-flow.md` 클래스명 갱신 | ✅ **통과** | `RunningApi.java:34-35`가 실제로 `RunningReader` + `RunningCommandService`를 주입받음. 문서 서술이 코드와 일치 |

---

### §1. FIX-1 검증 — 포인트컷 매칭 집합 전수 확인

**적용된 코드** (`src/main/java/soma/ghostrunner/global/common/log/aspect/SentrySpanAspect.java:31-39`):

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

#### (a) 연산자 결합 — 문제 없음

식 전체가 `execution(...)` 8개의 **순수 `||` 나열**입니다. `&&`가 한 번도 등장하지 않으므로 iter1이 경고한 `&&` > `||` 우선순위 문제가 발생할 여지가 구조적으로 없습니다. `||`는 결합법칙이 성립하므로 괄호 유무와 무관하게 결과가 동일합니다. `@Around`(:45-47)도 세 포인트컷의 `||` 나열이라 동일합니다.

#### (b) 새로 매칭되는 타입 — 전수 확인 결과 **8개, 전부 `application` 패키지**

`..`가 하위 패키지 전체를 의미하므로 `dto`·`domain`·`infra`·`api.support` 등 전 하위 트리를 훑었습니다. `course`/`running` 트리에서 `Reader`/`Writer`/`Resolver`로 끝나는 타입(중첩 클래스·인터페이스·record 포함)은 다음이 전부입니다:

| 타입 | 패키지 | 스테레오타입 | 비고 |
|---|---|---|---|
| `CourseReader` | course.application | `@Service` | **이번에 처음 프록시화** (TX 없음) |
| `CourseWriter` | course.application | `@Service` | 이미 `@Transactional`로 프록시 |
| `CourseSubscriptionWriter` | course.application | `@Service` | **이번에 처음 프록시화** (TX 없음) |
| `CourseReadModelReader` | course.application | `@Component` | **이번에 처음 프록시화** (TX 없음) |
| `CourseReadModelWriter` | course.application | `@Component` | 이미 `@Transactional(MANDATORY)`로 프록시 |
| `RegionResolver` | course.application | `@Service` | 이미 `@Transactional(NEVER)`로 프록시 |
| `RunningReader` | running.application | `@Service` | 이미 `@Transactional(readOnly)`로 프록시 |
| `RunningWriter` | running.application | `@Service` | 이미 `@Transactional`로 프록시 |

**`dto`·`domain`·`infra` 하위의 우발 매칭은 0건입니다.** `running.application.dto`, `running.api.dto`, `running.api.support`(`RunningApplicationMapper`, `RunningInfoFilter`), `course.dao`, `*.infra.persistence` 어디에도 세 접미사로 끝나는 타입이 없습니다. `*RepositoryImpl` 류 QueryDSL 구현체도 접미사가 `Impl`이라 걸리지 않습니다(그건 기존 `*Repository` 포인트컷도 마찬가지이며 이번 변경과 무관).

#### (c) 프록시 생성 가능성 — 전부 안전

- 8개 모두 `final` 클래스가 아니고, `public/protected final` 메서드도 0건입니다(CGLIB이 오버라이드 실패로 **조용히** advice를 건너뛰는 유형이 없음).
- 전부 인터페이스 없는 구상 클래스이므로 CGLIB 프록시가 만들어지고, 주입부도 전부 구상 타입(`private final CourseReader courseReader` 등)이라 `BeanNotOfRequiredTypeException` 위험이 없습니다.
- 새로 프록시화되는 3개(`CourseReader`, `CourseSubscriptionWriter`, `CourseReadModelReader`)에 대해 **`@MockitoSpyBean`·`AopTestUtils`·`ReflectionTestUtils.setField` 사용처 0건**을 확인했습니다. 테스트의 `ReflectionTestUtils.setField`는 전부 `Course`/`Member` **엔티티**의 id 주입이라 프록시와 무관합니다. (빈에 `setField`를 쓰고 있었다면 CGLIB 프록시 인스턴스에 필드가 꽂혀 타깃은 그대로인 채 테스트만 조용히 무의미해졌을 겁니다 — 이 유형이 없어서 다행입니다.)
- `new CourseReader(...)` 같은 수동 인스턴스화도 프로덕션 코드에 0건입니다.

#### (d) 기존 매칭 유실 — 0건

diff 상 **삭제된 라인이 없고 5줄이 순수 추가**입니다(`*Service`·`*Facade` 3줄 원형 유지). `apiPackagePointCut`·`repositoryPackagePointCut`도 무변경입니다. 유실 가능성이 구조적으로 없습니다.

#### (e) 다만 — 이건 "복구"보다 조금 넓습니다 (기록만 남기면 됨)

리팩토링 직전 베이스라인 `b319233`에서 `servicePackagePointCut`에 걸리던 course/running 애플리케이션 빈은 7개였습니다: `CourseFacade`, `CourseQueryService`, `CourseSubscriptionService`, `RegionService`, `PathSimplificationService`, `RunningCommandService`, `RunningQueryService`. 당시 **걸리지 않던** 빈은 `CourseWriter`, `RunningWriter`, `CourseReadModelReader`, `CourseReadModelWriter`, `CourseMapCacheEvictor`였습니다.

FIX-1 이후 집합은 **11개**입니다 — 위 7개의 개명 후 대응물 전부(= 복구 완료 ✅) **+ `CourseWriter`·`RunningWriter`·`CourseReadModelReader`·`CourseReadModelWriter` 4개(= 신규 계측)**. `CourseMapCacheEvictor`는 계획대로 제외됐습니다.

이 +4는 iter1 판정문 Task 1이 **명시적으로 승인한 범위**입니다("`*Writer`를 함께 넣는 이유 — 같은 문자열 한 곳을 편집하는 동일 작업"). 코드 경로로 보면 `CourseWriter`(← `CourseService`)와 `RunningWriter`(← `RunningCommandService`의 쓰기 TX 흡수)는 사실상 복구이고, 리드모델 2개는 `CourseQueryService`에서 떨어져 나온 조회 경로라 역시 준(準)복구입니다. **판정 자체는 문제 없습니다.** 다만 아래 RESIDUAL-1처럼 "before/after 빈 집합"을 설계 문서에 한 줄로 남겨 두어야, 다음에 Sentry 쿼터나 트레이스 볼륨이 튀었을 때 원인 후보에서 빠르게 지울 수 있습니다.

#### (f) `MemberVdotWriter` 제외 판단 — **맞습니다**

구현자의 판단이 정확합니다. 근거를 보강하면:

- `MemberVdotWriter`는 `soma/ghostrunner/domain/member/application/MemberVdotWriter.java`에 있습니다. 이 포인트컷은 처음부터 `domain.course..`/`domain.running..` **두 패키지로만 스코프**돼 있었고, `member`는 단 한 번도 계측 대상이었던 적이 없습니다.
- 즉 `member..*Writer`를 추가하는 것은 접미사 복구가 아니라 **계측 도메인을 하나 늘리는 스코프 확장**입니다. `docs/core/03-architecture.md:71`이 "다른 도메인(member, notice, device, auth, pacemaker)은 적용 대상이 아니다"라고 못박은 경계와도 정면으로 어긋납니다.
- 그리고 이 결정은 (e)의 +4와 모순되지 않습니다. 판단 기준이 "이 클래스가 과거에 걸렸는가"가 아니라 **"포인트컷의 패키지 스코프 안인가"**이기 때문입니다. course/running 안에서 접미사를 보강하는 것과 새 도메인을 편입하는 것은 성격이 다른 변경입니다. 후자는 iter1 DEFER-2(포인트컷 재설계 티켓)에서 대상 집합 변화를 문서화하며 다룰 사안입니다.

**결론: 추가하지 않은 것이 옳습니다.**

---

### §2. FIX-2 검증 — 정정된 문서 서술 ↔ 코드 대조

| 문서 서술 | 코드 사실 | 판정 |
|---|---|---|
| `RunningReader`가 `@Transactional(readOnly = true)`를 갖는다 | `RunningReader.java:37` 클래스 레벨에 **그대로 생존** | ✅ 일치 |
| 해당 조회 경로 4곳이 `@Transactional` 없음 | `CourseFacade.java:167`(`findPublicGhosts`), `:178`(`findTopRankingGhosts`), `:185`(`findTopPercentageGhosts`), `:222`(`findCourseStatistics`) — 네 곳 모두 어노테이션 없음 재확인 | ✅ 일치 |
| `open-in-view: false`라 떼면 지연 로딩이 깨진다 | `application-local.yml:17`, `application-dev.yml:23`에 설정. 저장소에 존재하는 프로필 yml은 이 둘뿐 | ✅ 일치 |
| 쓰기 TX 안에서 호출되면 호출자 TX에 참여한다 | 기본 전파 `REQUIRED` — 참여 시 바깥 TX의 readOnly 플래그가 우선. 서술 정확 | ✅ 일치 |
| `CourseSubscriptionWriter`는 자체 TX를 열지 않는다 | `CourseSubscriptionWriter.java:36-39` — `@Slf4j @Service @RequiredArgsConstructor`뿐, 클래스·메서드 어디에도 `@Transactional` **없음** | ✅ 일치 |
| "예외가 2건 있다" | 실제로는 **3건** (→ RESIDUAL-2) | ⚠️ 불완전 |

`RunningReader`의 어노테이션을 건드리지 않았고, `CourseSubscriptionWriter`에 `@Transactional`을 붙이지도 않았습니다(DEFER-1 유지). **코드 무변경 지시가 정확히 지켜졌습니다.**

---

### §3. FIX-3 검증 — 삭제로 인한 커버리지 공백

- `failsWithMemberNotFoundWhenMemberAbsent` 삭제 확인. 함께 사용되지 않게 된 import 3개(`MemberNotFoundException`, `ErrorCode`, `BusinessException`)와 static import `assertThatThrownBy`까지 정리됐습니다. **남은 `never`·`any`·`Optional` import는 여전히 사용 중**(`:123`, `:159`, `:214`)이라 과잉 삭제도 없습니다.
- 남은 7개 시나리오가 이 클래스의 핵심 계약을 그대로 덮습니다: 주인 구독 3건(없으면 생성 / soft delete면 **복원** / 이미 활성이면 무쓰기), 해제 2건(물리 삭제 아닌 soft delete / 없으면 무동작), 러너 구독 2건(없으면 생성 / **soft delete여도 복원하지 않는 멱등**). iter1이 "형태 중복이 아니라 규칙 차이"라고 평가한 두 진입점의 **비대칭 규칙이 양쪽 다 살아 있습니다.**
- 삭제된 테스트가 유일하게 덮던 것은 "`MemberService`가 던진 예외가 그대로 전파된다"인데, SUT에 catch/변환 코드가 없으므로 커버리지 손실이 아니라 **허위 커버리지 제거**입니다. 실 DB 검증 공백은 DEFER-3에 남아 있으며 이번 지적 대상이 아닙니다.
- `./gradlew test --tests "...CourseSubscriptionWriterUnitTest"` 실행 결과 **통과(exit 0)** 확인했습니다.

---

## 잘한 점

### 1. 리뷰어의 "권장 코드"를 검증 없이 채택하지 않았다

iter1 리포트 본문이 권장한 포인트컷에는 `(A || B) && @within(Service) || @within(Component)`라는 연산자 결합 버그가 있었고, 그대로 붙였다면 **저장소 전역의 모든 `@Component`(필터·클라이언트·리스너·config)에 AOP 프록시가 씌워질 뻔했습니다.** 채택된 코드는 `execution` 8개의 순수 `||` 나열이라 그 위험이 구조적으로 없습니다. 리뷰 피드백을 "지시"가 아니라 "가설"로 다루는 태도이고, 리팩토링 PR에서는 이게 정확한 태도입니다.

### 2. javadoc 경고가 "행동 가능한" 형태다

**코드** (`SentrySpanAspect.java:18-21`):

```java
 * <p><b>이 포인트컷은 클래스 이름 접미사에 의존한다 — 클래스 개명 시 반드시 이 파일을 함께 확인할 것.</b>
 * 컴파일러도 테스트도 이 유실을 잡지 못하고, 알게 되는 시점은 장애 대응 중 트레이스를 열었을 때다.
 * (사례: docs/design/reader-writer-layering.md 의 Reader/Writer 개명으로 {@code *Service} 접미사를 잃은
 * 클래스들의 스팬이 조용히 유실됐다.)
```

주의 문구는 대개 "주의하세요"로 끝나서 아무도 행동하지 않습니다. 이 문구는 (1) **언제** 확인해야 하는지(개명 시), (2) **왜** 자동으로 못 잡는지(컴파일러·테스트 무력), (3) **실제로 터진 사례**까지 담고 있습니다. 특히 (3)이 중요합니다 — 추상적 경고는 무시되지만 "실제로 한 번 터졌다"는 문장은 사람을 멈춰 세웁니다.

### 3. 삭제를 깔끔하게 했다

테스트 1개를 지우면서 미사용 import까지 정리한 건 사소해 보이지만, 이게 남으면 다음 사람이 "이 import가 왜 있지? 지워도 되나?"를 확인하는 데 시간을 씁니다. 그리고 `never`·`any`처럼 **아직 쓰이는 import는 남겨서** IDE 자동 정리에 휩쓸리지 않았습니다.

---

## 잔여 FIX 사항

> **잔여 FIX: 2건** (전부 MINOR, 전부 문서. 프로덕션 코드 수정 0건. 릴리스 블로커 아님)

### [RESIDUAL-1] 설계 문서 §7이 이번 FIX 자체를 반영하지 않았다 — 테스트 수 오류 + Sentry 변경 미기재

**현재 코드** (`docs/design/reader-writer-layering.md:196`):

```markdown
전체 빌드·테스트 그린(718 tests, 실패 0). **동작 변화 0** — 트랜잭션 경계·전파·예외 타입 전부 불변.
```

**문제점**:

두 가지가 어긋났습니다.

1. **`718 tests`는 이제 틀린 숫자입니다.** FIX-3이 테스트 1건을 삭제해 실제는 **717**입니다. 같은 브랜치 안에서 자기가 지운 테스트를 자기 문서가 모르고 있습니다. 숫자 하나라 사소해 보이지만, 이 문장의 용도가 "구현 결과의 검증 근거"라는 게 문제입니다. 6개월 뒤 누가 `./gradlew test`를 돌려 717이 나오면 "테스트가 하나 사라졌는데 언제 왜?"부터 조사하게 됩니다. iter1이 지적한 문서 부채와 정확히 같은 유형입니다 — **문서가 스스로 검증 가능하다고 주장하는 숫자를 틀리는 것.**

2. **`동작 변화 0` 단정이 여전히 각주 없이 서 있습니다.** iter1 MAJOR-1이 밝힌 대로 이 리팩토링은 Sentry 계측 커버리지를 실제로 바꿨고, FIX-1은 그걸 복구하면서 §1-(e)에서 확인했듯 베이스라인 대비 **4개 빈을 새로 계측 대상에 넣었습니다**. 트레이스 볼륨이 늘어나는 방향의 변경입니다. 이 사실이 저장소 어디에도 기록돼 있지 않고, 유일한 기록이 리뷰 문서 2개뿐입니다. 리뷰 문서는 "그때의 판단"을 남기는 곳이지 "현재의 사실"을 조회하는 곳이 아닙니다.

**개선 코드**:

```markdown
전체 빌드·테스트 그린(717 tests, 실패 0). **동작 변화 0** — 트랜잭션 경계·전파·예외 타입 전부 불변.

> **단, 관측(Sentry 스팬)은 예외다.** 개명으로 `SentrySpanAspect`의 `*Service` 접미사 포인트컷을
> 벗어난 4개 클래스의 스팬이 유실됐고, 후속 커밋 `585b870`에서 `*Reader`/`*Writer`/`*Resolver`
> 접미사를 추가해 복구했다. 이때 계측 대상이 베이스라인(`b319233`) 대비 **7개 → 11개**로 늘었다:
> 복구 7개(`CourseFacade`, `CourseReader`, `CourseSubscriptionWriter`, `RegionResolver`,
> `PathSimplificationService`, `RunningCommandService`, `RunningReader`) + 신규 4개
> (`CourseWriter`, `RunningWriter`, `CourseReadModelReader`, `CourseReadModelWriter`).
> `CourseMapCacheEvictor`는 AFTER_COMMIT 콜백이라 제외. 포인트컷 재설계는 별도 티켓.
```

**개선 이유**: 테스트 수는 **검증 가능한 주장**이라 틀리면 문서 전체의 신뢰도가 떨어집니다("이 숫자도 틀렸는데 나머지는?"). 그리고 계측 집합 before/after를 남겨 두면, 나중에 Sentry 트레이스 볼륨이나 쿼터가 튀었을 때 **원인 후보에서 이 PR을 30초 만에 지우거나 확정할 수 있습니다.** 관측 대상을 늘리는 변경은 비용(쿼터·샘플링·스토리지)이 따라오므로 "동작 변화 0"이라는 문장 아래 숨기면 안 됩니다.

---

### [RESIDUAL-2] TX 예외가 실제로는 3건인데 문서는 "예외 2건"으로 단정 + 같은 문서에 뜻이 다른 "예외 2건"이 두 번 나온다

**현재 코드** (`docs/core/03-architecture.md:66-70`):

```markdown
- **쓰기 `@Transactional`은 Writer에만 있다.** Reader는 쓰기 트랜잭션을 열지 않는다. 다만 **트랜잭션 경계를 클래스 이름만으로 단정하지 말 것** — 예외가 2건 있다.
  - `RunningReader`는 `@Transactional(readOnly = true)`를 갖는다(허용). ...
  - `CourseSubscriptionWriter`는 이름이 Writer지만 자체 TX를 열지 않고 호출자(`CourseWriter`/`RunningWriter`) TX에 참여한다.
- 구성: `RunningReader`/`RunningWriter`, `CourseReader`/`CourseWriter`, `CourseSubscriptionWriter`(...).
- **예외 2건**: `CourseMapCacheEvictor`(커밋 후 콜백이라는 특수 실행 문맥의 캐시 인프라), `RegionResolver`(...).
```

(`docs/design/reader-writer-layering.md:38-46`의 인용 블록도 같은 2건 목록입니다.)

**문제점**:

**(1) 세 번째 예외가 빠졌습니다.** `CourseReadModelWriter.java:44`는 클래스 레벨 `@Transactional(propagation = Propagation.MANDATORY)`를 갖습니다. 이건 **"이름은 Writer인데 자체 트랜잭션 경계를 열지 않는다"는 점에서 `CourseSubscriptionWriter`와 완전히 같은 부류**이고(방식만 javadoc 대신 어노테이션), TX 없이 호출하면 `IllegalTransactionStateException`으로 즉시 터집니다. 이 문서 문단이 정정하려는 오해가 바로 "Writer면 TX 경계다"인데, 그 오해의 가장 강한 반례가 목록에 없습니다.

실제 위험 시나리오: 누군가 `CourseFacade`에서 리드모델을 갱신하는 관리자 기능을 만들면서 문서의 "예외 2건"을 근거로 "`CourseReadModelWriter`는 예외가 아니니 자체 TX를 열겠지"라고 판단하고 `@Transactional` 없이 호출합니다. 다행히 MANDATORY라 즉시 예외가 나므로 프로덕션 사고는 아니지만, **문서를 믿고 짠 코드가 런타임에 배신당하는** 경험은 그 자체로 문서 신뢰도를 깎습니다. 게다가 `RunningWriter.saveRun` 안에서 `courseReadModelWriter.applyRun`과 `courseSubscriptionWriter.subscribeIfAbsent`가 나란히 호출되는데(iter1 MAJOR-2 참조), **같은 계약을 가진 이 두 줄 중 하나만 문서에 있는 상태**입니다.

**(2) 같은 화면에 "예외 2건"이 두 번, 뜻이 다르게 나옵니다.** :66의 "예외 2건"은 **트랜잭션 경계**의 예외(`RunningReader`, `CourseSubscriptionWriter`)이고, :70의 "예외 2건"은 **Reader/Writer 명명 규칙**의 예외(`CourseMapCacheEvictor`, `RegionResolver`)입니다. 다섯 줄 간격으로 같은 표현이 다른 집합을 가리키면, 급하게 읽는 사람은 둘을 같은 목록으로 착각합니다. 실제로 `RegionResolver`는 `@Transactional(NEVER)`라 **양쪽 주제에 다 걸치는** 클래스라서 혼동 가능성이 실재합니다.

**개선 코드**:

```markdown
- **쓰기 `@Transactional`은 Writer에만 있다.** Reader는 쓰기 트랜잭션을 열지 않는다.
  다만 **트랜잭션 경계를 클래스 이름만으로 단정하지 말 것** — 아래 3건이 반례다.

  | 클래스 | 실제 TX | 왜 |
  |---|---|---|
  | `RunningReader` | `@Transactional(readOnly = true)` | 호출자가 TX를 열지 않는 조회 경로(`CourseFacade`의 고스트 페이징·TOP 랭킹·상위 퍼센트·코스 통계)를 스스로 감싼다. `open-in-view: false`라 **떼면 지연 로딩이 깨진다.** 쓰기 TX 안에서 호출되면 호출자 TX에 참여 |
  | `CourseSubscriptionWriter` | 없음 | 이름은 Writer지만 자체 TX를 열지 않고 호출자(`CourseWriter`/`RunningWriter`) TX에 참여. (`MANDATORY` 승격은 별도 티켓) |
  | `CourseReadModelWriter` | `@Transactional(MANDATORY)` | 같은 계약을 어노테이션으로 강제한 형태 — **호출자 TX를 요구할 뿐 스스로 열지 않는다.** TX 없이 부르면 즉시 `IllegalTransactionStateException` |

- 구성: ...
- **명명 규칙의 예외 2건**: `CourseMapCacheEvictor`(...), `RegionResolver`(...).
```

**개선 이유**: 세 클래스를 한 표에 놓으면 **"Writer 이름은 TX 경계를 뜻하지 않는다"는 규칙이 사례 나열이 아니라 패턴으로 읽힙니다.** 특히 `CourseSubscriptionWriter`(javadoc만)와 `CourseReadModelWriter`(MANDATORY)를 나란히 두면, iter1 MAJOR-2/DEFER-1이 왜 "같은 계약인데 강제 장치가 한쪽에만 있다"고 지적했는지가 문서만 읽어도 보입니다 — 즉 **DEFER-1 티켓의 근거가 문서에 자동으로 남습니다.** 그리고 두 "예외 2건" 중 뒤쪽에 "명명 규칙의"를 붙이는 한 단어만으로 착독이 사라집니다.

---

## 새로 생긴 문제 — 없음

FIX 반영이 만든 신규 문제는 **프로덕션 코드·테스트 양쪽 모두 0건**입니다. 특히 다음을 확인했습니다:

- 우발적 포인트컷 매칭(`dto`/`domain`/`infra` 하위) — 0건
- 기존 매칭 유실 — 0건 (삭제 라인 없음)
- `final` 클래스/메서드로 인한 무언(無言) advice 누락 — 0건
- 새로 프록시화되는 3개 빈에 대한 `SpyBean`/`ReflectionTestUtils.setField`/수동 인스턴스화 충돌 — 0건
- 테스트 파일의 과잉/부족 import 정리 — 정확함
- 스팬에 인자·PII가 실리는지 — 스팬 이름은 `joinPoint.getSignature().getName()`(메서드명)뿐이라 없음

한 가지 **관찰**(지적 아님): `CourseFacade.java:196`의 `runningReader.findCourseRunStatistics(...)`는 페이지 루프 안 호출이라 페이지 크기에 비례해 스팬이 생깁니다. 다만 그 루프는 이미 `*Repository` 포인트컷으로 쿼리당 스팬을 만들고 있어 **증가분은 대략 2배 수준**이고, 애초에 N+1 자체가 기존 이슈라 이번 변경의 책임이 아닙니다. 리드모델 후속 작업에서 함께 볼 항목입니다.

---

## 설계 문서 대비 차이

| 항목 | iter1 판정문 지시 | 구현 | 판정 |
|------|------|------|------|
| Task 1 — 포인트컷 접미사 추가 | course에 `Reader`/`Writer`/`Resolver`, running에 `Reader`/`Writer` 5줄 | 정확히 5줄 추가, 기존 3줄 유지 | ✅ 일치 |
| Task 1 — "권장" 재설계안 채택 금지 | `&&`/`\|\|` 우선순위 버그로 채택 불가 | 미채택. 순수 `\|\|` 나열 유지 | ✅ 일치 |
| Task 1 — javadoc 경고 명시 | "이름 접미사 의존 — 개명 시 확인" + 사례 | :18-21에 경고 + 사례 + "왜 자동으로 못 잡는지"까지 | ✅ 일치(지시 이상) |
| Task 1 — `CourseMapCacheEvictor` 제외 | `*Evictor` 넣지 말 것 | 미포함 | ✅ 일치 |
| Task 1 — `member` 도메인 | (지시 없음) | `MemberVdotWriter` 제외 — 스코프 확장 회피 | ✅ 판단 타당 |
| Task 2 — 문서만, 코드 무변경 | `RunningReader` 어노테이션 절대 유지 | `RunningReader.java:37` 생존, `CourseSubscriptionWriter` 무TX 유지 | ✅ 일치 |
| Task 2 — 예외 2건 명시 | `RunningReader`, `CourseSubscriptionWriter` | 2건 명시 완료. 단 `CourseReadModelWriter`가 3번째 사례 | ⚠️ 불완전 (→ RESIDUAL-2) |
| Task 3 — 삭제만, 통합 승격 금지 | 테스트 삭제 + 미사용 import 정리 | 삭제 + import 4개 정리, 통합 테스트 미추가 | ✅ 일치 |
| Task 3 — 718 → 717 | 테스트 수 감소 확인 | 코드는 반영, **문서 §7은 718로 방치** | ⚠️ 미반영 (→ RESIDUAL-1) |
| Task 4 — `pacemaker-llm-flow.md:17` | `RunningQueryService` → `RunningReader` | 치환 완료. `RunningApi.java:34-35`와 일치 | ✅ 일치 |
| Task 4 — 스냅샷 문서는 그대로 | `01-current-state.md:105` 유지 | 유지됨. 잔존 옛 이름은 전부 스냅샷·개명 대응표·이력 각주 안 | ✅ 일치 |
| DEFER-1 — MANDATORY 승격 | 이번엔 하지 말 것 | 미적용 | ✅ 일치 |
| DEFER-2/3/4 | 별도 티켓 | 미적용 | ✅ 일치 |

---

## 선배 개발자의 한마디

**첫째, 이번에 가장 잘한 건 코드가 아니라 "리뷰어 코드를 안 믿은 것"입니다.** iter1 본문의 권장 포인트컷을 그대로 붙였다면 관측 회귀 하나를 고치려다 저장소 전체 `@Component`에 프록시를 씌우는, 훨씬 큰 사고가 났을 겁니다. 리뷰 코멘트에 붙은 코드 블록은 **검증된 패치가 아니라 리뷰어가 머릿속에서 컴파일한 초안**입니다. 저는 리뷰에서 받은 코드를 붙일 때 "이 사람이 이걸 실행해 봤을까?"를 항상 한 번 묻습니다. 이번엔 그 질문을 한 덕분에 살았습니다. 앞으로도 그렇게 하세요.

**둘째, 문서를 고치는 커밋일수록 문서에 대한 검증이 필요합니다.** 이번 FIX 4건 중 3건이 문서였는데, 그 3건이 새 사실 오류(`718 tests`)와 불완전한 목록(`예외 2건`)을 남겼습니다. 이건 실력 문제가 아니라 **문서에는 테스트가 없다**는 구조적 문제입니다. 코드는 틀리면 빌드가 깨지지만 문서는 틀려도 아무 일도 안 일어나고, 그래서 6개월 뒤 누군가 그 문장을 근거로 삼을 때까지 조용히 살아 있습니다. 방법은 하나뿐입니다 — **문서에 숫자나 목록을 쓸 때는 그걸 만들어낸 명령을 함께 남기는 것.** "717 tests"라면 `./gradlew test` 결과, "예외 3건"이라면 그 목록을 뽑은 grep 한 줄. 검증 명령이 붙은 주장은 다음 사람이 30초 만에 재확인하고, 검증 명령이 없는 주장은 영원히 아무도 확인하지 않습니다.

마지막으로: FIX-1은 **깔끔하게 잘 끝났습니다.** 관측 유실은 "장애 한복판에서만 발견되는" 유형이라 복구 타이밍이 전부인데, 원인을 아는 사람이 같은 주에 고쳤고 재발 방지 경고까지 코드에 남겼습니다. 남은 2건은 문서 몇 줄이니 다음 커밋에 얹어서 정리하고 나가세요.
