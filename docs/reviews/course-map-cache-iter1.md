# 코스 지도 캐시키 재설계 (regionId) — 코드 품질 리포트 (iter1)

- 브랜치: `refactor/course-map-cache`
- 리뷰 범위: 캐시키 교체 사이클 (워킹트리 = `HEAD(777fa89)` 대비 변경분). PR-2 회수분은 제외
- 설계 문서: `docs/refactoring/course-read-model/cache/05-cache-key-design.md`
- 검증 상태: compileJava/compileTestJava 통과, 관련 테스트 19건 그린

## 총점: 84/100 — 등급 A (약간의 개선 후 출시 가능. 단, CRITICAL 2건은 출시 전 처리 권장)

## 차원별 점수

| 차원 | 점수 | 핵심 피드백 |
|------|------|-------------|
| 가독성 | 9/10 | 네이밍이 의도를 드러냄. Facade javadoc의 HTML 표는 과함 |
| 아키텍처 준수 | 9/10 | 설계 §6 배치 그대로, 외부 API 선택 파라미터 추가만 |
| 단일 책임 | 9/10 | Facade 파라미터 7개는 파라미터 객체 신호 |
| 캡슐화 | 9/10 | 불변식을 타입으로 강제. "@Transactional 금지"는 주석뿐 |
| 테스트 품질 | 8/10 | §8의 6개 시나리오 정확히 커버. MapStruct 계약만 무방비 |
| 에러 처리 | 7/10 | 404가 홈을 막고, region 경로 로그/지표 0 |
| 성능 | 9/10 | 히트 시 DB 0회가 테스트로 고정됨. 스탬피드 미대비 |
| 보안 | 6/10 | 무제한 행 생성 + 동네 단위 캐시 오염 가능 |
| 설계 일치도 | 10/10 | 전부 일치. CRITICAL 2건은 설계 빈틈을 구현이 충실히 따라간 결과 |
| 유지보수성 | 8/10 | 이름 정규화 부재는 지금은 공짜, 나중엔 마이그레이션 |

## 잘한 점 (요약)

1. **대표좌표 조회를 @Cacheable 메서드 안에 배치** — 히트 시 DB 0회를 온전히 회수, 테스트가 키 문자열·TTL까지 고정
2. **캐시 값 결정성을 시그니처로 강제** — findCoursesForMapByRegion(Long regionId) 인자가 regionId뿐이라 "같은 키 다른 값" 사고가 물리적으로 불가능. Region에 setter 없음
3. **트랜잭션 없는 멱등 upsert + 원인 예외 보존** — rollback-only 오염 진단 정확, orElseThrow(() -> raceLost)
4. **SecurityConfig 블랙리스트 등록** — 블랙리스트 방식의 신규 API 인증 누락 함정 회피
5. **관측 선행(enableStatistics)** — 시뮬레이션 예측 → 실측 검증 루프
6. **핵심 로직만 테스트** — §8의 6건 그대로, 과잉 없음

## 개선 필요 사항

### [CRITICAL-1] 최초 등록자가 동네 전체의 지도 결과를 영구 결정 — 검증·복구 경로 없음
- RegionService.resolve: 좌표 검증이 전지구 범위(@Min/@Max)뿐. 공격자/오류 좌표(0,0)가 대표좌표로 확정되면 그 동네 전원이 빈 지도 — 히트율 문제가 아니라 **데이터 문제** (§3-2 "정합성 무관" 전제 오류)
- 개선: ① 서비스 영역(한국 bbox) 검증을 **신규 생성 시점에만** ② 기존 행과 10km 이상 어긋난 재요청 WARN 로그(좌표는 남기지 않음 — 개인위치정보) ③ 자동 보정은 금지(결정성 훼손)

### [CRITICAL-2] 미발급 regionId 404가 홈 화면을 막음 — dev(ddl-auto: create) 배포마다 재현 확정
- 설계 §5-1 "홈 화면을 막지 않는다" ↔ §5-2 "404" 자기모순. FE는 regionId를 persist하고 3km 이동 시에만 갱신 → dev 배포 직후 QA 기기 전원 홈 404
- 개선(권장): Facade에서 RegionNotFoundException catch → WARN 로그 + 좌표 폴백 강등 (빈 결과 캐시 오염 없음 — 예외는 캐싱 안 됨). 404 유지 시엔 FE에 "404 → regionId 폐기 후 재요청" 계약 필수

### [MAJOR-1] 지역 이름 정규화 부재 — iOS(NFD)/Android(NFC)가 같은 동네를 다른 키로 분열
- 개선: 저장 전 Normalizer.NFC + trim + 공백 축약 3줄. **첫 행이 들어오기 전까지만 공짜**

### [MAJOR-2] 광역 줌 요청이 regionId를 달고 오면 2km 결과를 조용히 반환
- 캐시 경로 판정에 radiusM 미고려 — FE 규율에만 의존. 개선: radiusM <= 3000 조건을 useRegionCache 판정에 추가 (서버 자체 방어)

### [MAJOR-3] POST /v1/regions 무제한 행 생성 허용
- 개선: name @Pattern(한글/영문/숫자/공백) + 신규 생성에만 레이트리밋 (기존 조회는 무제한 — 정상 트래픽 무영향)

### [MAJOR-4] "@Transactional 금지" 불변식이 주석뿐 — 트랜잭션 있는 호출자가 생기면 UnexpectedRollbackException이 호출자 쪽에서 터짐
- 개선: @Transactional(propagation = Propagation.NEVER) — 계약을 런타임 강제, 실패 지점 = 원인 지점

### [MINOR-1] MapStruct id→regionId 매핑 무검증 (지워져도 컴파일 통과, regionId: null 침묵)
### [MINOR-2] 캐시 스탬피드 미대비 — 키가 뭉칠수록 만료 동시 미스 집중. 지표 관측 후 sync=true 판단
### [MINOR-3] Facade javadoc HTML 표가 설계 §5-2와 중복 — 참조 한 줄로 대체 권장
### [MINOR-4] CourseFacadeTest 캐시 정리가 @BeforeEach가 아닌 본문 — 플래키 위험
### [MINOR-5] 무관한 리팩토링(stream→for) 혼입 — 커밋 분리 권장

## 설계 문서 대비 차이
설계 일치도 10/10 — CRITICAL 2건은 구현 이탈이 아니라 **설계 자체의 빈틈을 구현이 충실히 따라간 것**. 문서와 코드를 함께 수정해야 함.

---

## 실용주의 판정 (Pragmatic Action Decision)

> 판정자: Pragmatic Action Decider
> 판정 기준: The Pragmatic Programmer (Andrew Hunt & David Thomas) 원칙 기반
> 작업 범위: `refactor/course-map-cache` 브랜치의 **BE 단독** 캐시키 교체 사이클 (반올림 키 → regionId). `region` 테이블 신규 DDL 포함, **아직 프로덕션 배포 전·행 0개**. FE(sgmrt-app)는 미착수 — 롤아웃 §9에 따라 BE 선배포.
> 전제 상태: 전체 테스트 698건 그린, compileJava/compileTestJava 통과.

### 판정 요약

| 이슈 | 심각도 | 판정 | 근거 원칙 |
|------|--------|------|-----------|
| [CRITICAL-1] 최초 등록자가 동네 지도 결과를 영구 결정 | CRITICAL | 🔴 FIX | 되돌림 가능성(#18) + 새벽 3시 장애 콜(#38) |
| [CRITICAL-2] 미발급 regionId 404가 홈을 막음 | CRITICAL | 🔴 FIX | 새벽 3시 장애 콜(#38) + 깨진 유리창(#5, 설계 §5-1 자기모순) |
| [MAJOR-1] 지역 이름 정규화 부재 (NFD/NFC 분열) | MAJOR | 🔴 FIX | 되돌림 가능성(#18) — **지금이 비용 0인 마지막 창** |
| [MAJOR-2] 광역 줌 + regionId → 2km 결과 침묵 반환 | MAJOR | 🔴 FIX | 우연에 의한 프로그래밍(#62) + 직교성(#17) |
| [MAJOR-3] POST /v1/regions 무제한 행 생성 | MAJOR | 🔴 FIX (입력 검증) / 🟡 DEFER (레이트리밋) | 새벽 3시 장애 콜(#38) / Good Enough(#8) |
| [MAJOR-4] "@Transactional 금지"가 주석뿐 | MAJOR | 🔴 FIX | 우연에 의한 프로그래밍(#62) — 계약을 런타임 강제 |
| [MINOR-1] MapStruct id→regionId 매핑 무검증 | MINOR | 🟡 DEFER | Good Enough(#8) — 올바른 해법(매퍼 전역 정책)이 범위 밖 |
| [MINOR-2] 캐시 스탬피드 미대비 | MINOR | 🟡 DEFER | 우연에 의한 프로그래밍 역방향 — 관측 없는 최적화 금지 |
| [MINOR-3] Facade javadoc HTML 표 중복 | MINOR | 🟢 PASS | CRITICAL-2 Task에 흡수 (독립 작업 불필요) |
| [MINOR-4] 테스트 캐시 정리가 본문에 위치 | MINOR | 🟡 DEFER | 되돌림 가능성(#18) — 나중 비용 = 지금 비용 |
| [MINOR-5] 무관한 리팩토링(stream→for) 혼입 | MINOR | 🟢 PASS | You Can't Write Perfect Software(#36) — 동작 동등, 되돌릴 이유 없음 |

**FIX 6건 / DEFER 4건 / PASS 2건.**

등급 A(84점)에서 MAJOR 4건이 모두 FIX로 판정된 것은 완벽주의가 아니라 **타이밍** 때문이다. 4건 중 3건(MAJOR-1·2·3)은 "지금 안 하면 비싸지는" 창이 열려 있다 — `region` 테이블에 첫 행이 들어가기 전, FE가 배포되기 전. 남은 1건(MAJOR-4)은 1줄이다. 되돌림 가능성 테스트(#18)를 통과하지 못하는 항목만 FIX로 올렸고, 되돌리기 쉬운 것은 전부 DEFER/PASS로 내렸다.

---

### 수정 필수 항목 (FIX Tasks)

#### Task: [CRITICAL-1] 신규 지역 등록 시점에만 서비스 영역 좌표 검증 + 이탈 재요청 WARN

- **판정**: 🔴 FIX
- **근거 원칙**: 되돌림 가능성 — *"There Are No Final Decisions." — Tip #18*. `Region`은 수정 메서드가 없어 대표좌표가 **영구 불변**이고(설계 §6-1), 오염 행의 복구 수단은 운영자의 수동 DELETE뿐이다(§3-2 "soft delete 없음"). 검증을 나중에 넣으면 이미 적재된 오염 행은 검증 대상 밖이라 데이터 마이그레이션이 따라붙는다. 추가로 새벽 3시 장애 콜(#38) — (0,0) 좌표가 대표좌표로 확정되면 그 동네 전원의 홈 지도가 영구 공백이며, 이는 히트율 저하가 아니라 **사용자에게 보이는 오답**이다.
- **수정 대상**:
  - `src/main/java/soma/ghostrunner/domain/course/application/RegionService.java:32-39` (`saveNewRegion`)
  - `docs/refactoring/course-read-model/cache/05-cache-key-design.md` **§3-2 표 마지막 행**("대표좌표 이상치는 신뢰하고 수용 / 정합성 무관")과 **§5-1 검증 목록**
- **수정 내용**:
  1. `saveNewRegion` 진입부에 **서비스 영역(한국 bbox) 검증**을 추가한다. 이탈 시 400 계열 도메인 예외(신규 ErrorCode 또는 기존 `InvalidRequest` 계열 재사용). 상수는 `RegionService` 내부 private static final로 두고 외부 노출하지 않는다.
  2. 검증은 **신규 생성 경로에만** 건다. `resolve`의 `findByName` 히트 경로는 손대지 않는다 — 이미 등록된 동네의 정상 트래픽(대다수)은 검증 비용 0.
  3. 기존 행이 있는데 요청 좌표가 대표좌표와 **10km 이상** 어긋나면 `log.warn`. **좌표값은 로그에 남기지 않는다**(개인위치정보) — `regionId`, `name`, 거리(km)만 기록.
  4. **자동 보정은 금지.** 대표좌표를 갱신하면 같은 키의 캐시 값이 달라져 §4의 결정성 근거가 무너진다.
  5. 설계 문서 §3-2의 "대표좌표 이상치는 신뢰하고 수용 — 오염돼도 캐시 히트율에만 영향, 정합성 무관"을 **삭제하고**, "서비스 영역 검증으로 1차 방어, 영역 내 이상치는 수용(폴리곤 부재), 이탈 재요청은 WARN으로 관측"으로 교체한다. 이 문장이 CRITICAL-1의 발원지다.
- **예상 작업량**: S
- **검증 방법**: `RegionServiceTest`(통합)에 **경계 케이스 1건만** 추가 — 서비스 영역 밖 좌표로 신규 등록 시 예외, 그리고 **기존 행이 있으면 영역 밖 좌표로 요청해도 기존 id를 반환**(검증이 조회 경로를 막지 않음). 좌표별 망라 테스트는 만들지 않는다(프로젝트 원칙: 핵심 로직만). WARN 로그는 테스트 대상 아님.

---

#### Task: [CRITICAL-2] 미발급 regionId를 404 대신 좌표 폴백으로 강등

- **판정**: 🔴 FIX
- **근거 원칙**: 새벽 3시 장애 콜 — *"Crash Early... A dead program normally does a lot less damage than a crippled one." — Tip #38*의 **경계**를 짚는 사례다. Crash Early는 "복구 경로가 없을 때" 유효하다. 여기엔 완전한 복구 경로(좌표 폴백)가 이미 구현돼 있고, 그것이 `regionId` 없는 요청의 정상 동작이다. 복구 가능한 상황에서 홈 화면 전체를 죽이는 것은 Crash Early가 아니라 **불필요한 자해**다. 더해 깨진 유리창(#5) — 설계 §5-1이 "실패해도 FE는 regionId 없이 코스 조회 가능 (폴백) — **홈 화면을 막지 않는다**"를 이미 사용자 승인된 원칙으로 못박았는데 §5-2의 404가 이를 정면으로 뒤집는다. 승인된 원칙과 어긋난 쪽(404)을 원칙에 맞추는 것이 정합 방향이다.
- **확정 근거(현장 확인)**: `src/main/resources/application-dev.yml:22` = `ddl-auto: create`. dev 배포마다 `region` 테이블이 재생성되어 id가 초기화되는 반면, FE는 `regionId`를 zustand persist로 보관하고 3km 이동 시에만 갱신한다(설계 §7-1). 즉 **dev 배포 직후 QA 기기 전원의 홈 화면 백지가 배포마다 확정 재현**된다. 가능성이 아니라 스케줄된 사고다.
- **수정 대상**:
  - `src/main/java/soma/ghostrunner/domain/course/application/CourseFacade.java:165-171` (`findCoursesByPosition`)
  - `src/main/java/soma/ghostrunner/domain/course/application/CourseFacade.java:146-160` (javadoc HTML 표 — MINOR-3 흡수)
  - `docs/refactoring/course-read-model/cache/05-cache-key-design.md` **§5-2 표 4행**, **§5-2 "*404 선택 이유*" 문단**, **§5-3**
- **수정 내용**:
  1. Facade에서 `RegionNotFoundException`을 catch → `log.warn`(regionId 기록) 후 `courseReadModelReader.findCoursesForMap(lat, lng, radiusM)`로 강등. catch는 **반드시 Facade(= `@Cacheable` 프록시 바깥)**에 둔다. Reader 내부에서 잡으면 캐시 프록시가 폴백 결과를 `course-map::{regionId}`에 적재해 **없는 지역의 좌표 기반 결과가 캐시에 오염 적재**된다. 현재처럼 예외가 프록시를 뚫고 나가면 Spring Cache는 적재하지 않는다.
  2. `RegionNotFoundException`과 `ErrorCode.REGION_NOT_FOUND(C-005)`는 **삭제하지 말고 유지**한다 — Reader→Facade 간 내부 신호로 계속 쓰인다. 다만 외부 API로는 노출되지 않으므로 ErrorCode에 그 취지를 주석으로 남긴다.
  3. 설계 문서 §5-2 표 4행을 `404 C-005` → `WARN 로그 + 좌표 폴백 강등 (홈을 막지 않는다 — §5-1 원칙)`으로 교체하고, "*404 선택 이유*" 문단 전체를 폴백 선택 근거(dev `ddl-auto: create` 재현성 + §5-1 원칙 일치 + 예외 미캐싱은 Facade catch로도 유지됨)로 교체한다. §5-3에는 C-005가 내부 신호임을 1줄 명시.
  4. Facade javadoc의 HTML `<table>`을 **설계 §5-2 참조 한 줄로 축약**한다(MINOR-3 해소). 이번 수정으로 표 내용이 낡으므로, 낡은 표를 고치는 대신 중복 자체를 제거한다 — *"DRY" — Tip #15*.
- **예상 작업량**: S
- **검증 방법**: `CourseFacadeTest`(통합)에 **1건만** 추가 — 존재하지 않는 regionId로 기본 요청 시 ① 예외 없이 요청 좌표 기준 결과가 반환되고 ② `course-map::*` 키가 생성되지 않는다(오염 미적재). 기존 `clearCourseMapCache()`와 `COURSE_MAP_KEY_PATTERN` 재사용. 미존재 regionId → 예외를 검증하던 기존 Reader 테스트는 **Reader 계층 계약으로는 그대로 유효**하므로 유지한다(Reader는 계속 던지고, Facade가 잡는다).
- **FE 영향**: **없음.** 404를 유지했다면 "404 → regionId 폐기 후 재요청" 계약을 FE 티켓으로 분리해야 했으나, 서버 폴백을 택함으로써 FE는 설계 §7 그대로 진행할 수 있다. 이번 브랜치는 BE 범위이며 FE 티켓 분리는 불필요하다.

---

#### Task: [MAJOR-1] 지역 이름 정규화(NFC + trim + 공백 축약)를 저장·조회 양쪽에 적용

- **판정**: 🔴 FIX
- **근거 원칙**: 되돌림 가능성 — *"There Are No Final Decisions." — Tip #18*. 이 항목의 비용 곡선은 계단형이다. `region` 테이블은 아직 프로덕션에 없고 행이 0개다(설계 §9: "백필 불필요 — 데이터는 사용자 요청이 채운다"). **지금은 3줄, 첫 행이 들어온 뒤에는 데이터 마이그레이션 + `uk_region_name` 재구성 + 분열된 키의 캐시 병합**이다. 리포트의 "첫 행이 들어오기 전까지만 공짜"가 정확하다. 덧붙여 설계 §3-1은 이미 `name`을 "**정규화된** 전체 경로"로 규정했다 — 이건 개선 제안이 아니라 **설계 미구현**이다.
- **수정 대상**:
  - `src/main/java/soma/ghostrunner/domain/course/application/RegionService.java:27-30` (`resolve`)
  - `docs/refactoring/course-read-model/cache/05-cache-key-design.md` **§3-2 표**(정규화 규칙 행 추가), **§6-3**(정규화 위치 명시)
- **수정 내용**:
  1. `resolve` **진입 즉시** 정규화한 문자열을 만들고, 이후 `findByName`·`save` 모두 정규화된 값만 사용한다. `java.text.Normalizer.normalize(name, Normalizer.Form.NFC)` → `trim()` → 연속 공백을 단일 공백으로 축약(`replaceAll("\\s+", " ")`).
  2. 정규화는 **`RegionService`의 단일 지점**에 둔다. Controller나 DTO에 흩어놓으면 "정규화된 이름만 저장된다"는 불변식의 소유자가 사라진다 — *"Eliminate Effects Between Unrelated Things" — Tip #17*.
  3. 응답의 `name`은 정규화된 값을 반환한다(저장된 값 = 반환값). FE가 보낸 원문과 다를 수 있으나 FE는 `name`을 표시 용도로 쓰지 않는다(§7-2는 `regionId`만 persist).
  4. 설계 문서 §3-2 표에 "이름 정규화: NFC + trim + 공백 축약, `RegionService.resolve` 진입점 단일 적용 — iOS(NFD)/Android(NFC) 키 분열 방지" 행을 추가하고, §6-3 코드 스니펫에도 반영한다.
- **예상 작업량**: S
- **검증 방법**: `RegionServiceTest`(통합)에 **1건만** 추가 — NFD로 조합된 이름과 NFC 이름, 그리고 앞뒤/중간 공백이 섞인 이름이 **모두 같은 regionId로 해소**된다. 유니코드 형태별 망라 테스트는 만들지 않는다.

---

#### Task: [MAJOR-2] 캐시 경로 판정에 radiusM 상한 가드 추가 (서버 자체 방어)

- **판정**: 🔴 FIX
- **근거 원칙**: 우연에 의한 프로그래밍 — *"Don't Program by Coincidence. Rely only on reliable things." — Tip #62*. 현재 서버 응답의 정확성은 **FE가 §7-1의 "지도 중심 ≈ 사용자 GPS 500m 이내" 규칙을 지킨다는 가정**에만 의존한다. 이 가정은 서버가 검증하지도, 관측하지도 못한다. FE가 규칙을 어기거나 회귀하면 서버는 광역 줌 요청에 2km 결과를 **조용히** 돌려준다 — 예외도, 로그도, 히트율 지표 이상도 없는 침묵 오답이다. 더해 직교성(#17) — BE의 정확성이 별도 저장소의 FE 구현 디테일에 결합돼 있다.
- **타이밍 근거**: 롤아웃 §9가 **BE 선배포**를 확정했다. FE가 `regionId`를 붙이기 시작하는 시점에 서버 가드가 이미 존재해야 한다. 지금 1줄로 넣지 않으면 FE 배포와 BE 후속 PR 사이에 창이 열린다.
- **수정 대상**:
  - `src/main/java/soma/ghostrunner/domain/course/application/CourseFacade.java:168` (`useRegionCache` 판정)
  - `docs/refactoring/course-read-model/cache/05-cache-key-design.md` **§5-2 표 1행**, **§4 "고정 반경 2km인 이유" 문단**
- **수정 내용**:
  1. `useRegionCache` 조건에 `radiusM <= REGION_CACHE_MAX_RADIUS_M`(3000)을 추가한다. 상한 초과 요청은 조용히 좌표 폴백 경로로 내린다(에러 아님 — 정상 폴백).
  2. 상수는 Facade에 private static final로 두고, "지역 캐시 값은 고정 2km — 그보다 크게 요청한 뷰포트에 2km 결과를 주지 않기 위한 서버 자체 방어(FE 규율에 의존하지 않는다)"를 한 줄 주석으로 남긴다. **왜 3000인지**(2km 고정값 + 뷰포트 오차 여유)를 함께 적는다.
  3. 설계 문서 §5-2 표 1행 조건을 "regionId 있음 + 기본 요청 + `radiusM <= 3000`"으로 고치고, §4의 "요청의 radiusM은 regionId 경로에서 무시된다" 문장 뒤에 "단, 상한(3000m) 초과 요청은 캐시 경로 자체를 타지 않는다 — 무시와 오답은 다르다"를 덧붙인다.
- **예상 작업량**: S
- **검증 방법**: `CourseFacadeTest`(통합)에 **1건만** 추가 — 유효한 regionId + 기본 요청이지만 `radiusM = 10000`이면 요청 좌표 기준 결과가 나오고 `course-map::*` 키가 생기지 않는다. 기존 "비기본 요청 우회" 테스트와 같은 픽스처 재사용.

---

#### Task: [MAJOR-3] 지역 이름 형식 검증(@Pattern) 추가 — 레이트리밋은 분리

- **판정**: 🔴 FIX (입력 검증만) / 🟡 DEFER (레이트리밋)
- **근거 원칙**: 새벽 3시 장애 콜(#38) — 현재 `@NotBlank @Size(max=100)`만으로는 임의 100자 문자열이 전부 새 행 + 새 캐시 키가 된다. 캐시 키 카디널리티 상한을 "활성 지역 수 수백~수천"으로 예측한 §4의 메모리 추정이 통째로 무효화된다. 다만 **JWT 인증 벽이 1차 방어로 이미 존재**하므로(설계 §5-1) 무인증 대량 유입은 불가하다. 이 사실이 FIX 범위를 입력 검증으로 좁히는 근거다 — *"You Can't Write Perfect Software" — Tip #36*.
- **수정 대상**:
  - `src/main/java/soma/ghostrunner/domain/course/dto/request/RegionResolveRequest.java:17-19` (`name` 필드)
  - `docs/refactoring/course-read-model/cache/05-cache-key-design.md` **§5-1 검증 목록**
- **수정 내용**:
  1. `name`에 `@Pattern(regexp = "[가-힣a-zA-Z0-9 ]+")`를 추가한다. 한글 완성형/영문/숫자/공백만 허용 — 실제 OS 리버스 지오코딩 결과가 이 범위를 벗어나지 않는다.
  2. **MAJOR-1 정규화(NFC)와 순서 주의**: `@Pattern`은 DTO 바인딩 시점(정규화 전)에 평가된다. iOS가 보내는 NFD 조합형 한글은 `가-힣` 범위에 걸리지 않으므로, 자모 범위(`ㄱ-ㅎㅏ-ㅣ`)를 정규식에 함께 허용하거나 **정규화를 DTO가 아닌 `RegionService`에서 수행하는 현 설계를 유지하되 `@Pattern`에 자모를 포함**해야 한다. 이 상호작용을 놓치면 iOS 등록이 전량 400으로 막힌다 — MAJOR-1과 **반드시 같은 커밋에서** 처리한다.
  3. 설계 문서 §5-1의 검증 줄("`name` @NotBlank @Size(max=100)")에 `@Pattern` 규칙과 NFD 자모 허용 이유를 명시한다.
- **예상 작업량**: S
- **검증 방법**: `RegionServiceTest`가 아니라 **DTO 제약 단위 검증 1건** — NFD 조합형 한글 이름이 `@Pattern`을 통과하는지(iOS 회귀 방지). 이것이 이 Task의 유일한 실질 위험 지점이다. 허용 문자 전수 테스트는 만들지 않는다.

---

#### Task: [MAJOR-4] `@Transactional(propagation = NEVER)`로 불변식을 런타임 강제

- **판정**: 🔴 FIX
- **근거 원칙**: 우연에 의한 프로그래밍 — *"Don't Program by Coincidence." — Tip #62*. `RegionService`의 복구 경로(유니크 충돌 → 재조회)가 성립하는 유일한 이유는 "호출자가 트랜잭션을 열지 않았다"는 **검증되지 않은 가정**이다. javadoc(`RegionService.java:15-19`)이 이를 강하게 경고하지만, **주석은 실행되지 않는다**. 트랜잭션 있는 호출자가 생기면 rollback-only 오염으로 `UnexpectedRollbackException`이 **호출자 쪽에서** 터진다 — 실패 지점과 원인 지점이 분리되어 새벽 3시에 추적 불가능한 형태가 된다(#38).
- **수정 대상**:
  - `src/main/java/soma/ghostrunner/domain/course/application/RegionService.java:21-23` (클래스 선언부)
  - `docs/refactoring/course-read-model/cache/05-cache-key-design.md` **§6-3** ("클래스에 트랜잭션을 걸지 않은 이유" 문단 + 코드 스니펫 주석)
- **수정 내용**:
  1. 클래스에 `@Transactional(propagation = Propagation.NEVER)`를 붙인다. 기존 javadoc은 **삭제하지 않는다** — 애노테이션은 "무엇을"을, javadoc은 "왜"를 말한다. javadoc 첫 줄만 "애노테이션으로 강제됨"으로 갱신한다.
  2. 설계 문서 §6-3의 코드 스니펫 주석 `// 의도적으로 @Transactional 없음 (아래)`을 `@Transactional(propagation = Propagation.NEVER)` 실제 코드로 교체하고, 아래 설명 문단에 "주석이 아니라 런타임 계약 — 위반 시 호출자 쪽이 아니라 **진입 즉시** `IllegalTransactionStateException`으로 실패한다"를 추가한다.
- **예상 작업량**: S
- **검증 방법**: **새 테스트를 추가하지 않는다.** 기존 `RegionServiceTest`(통합, 멱등 등록)와 전체 테스트 698건이 그린으로 유지되면 현재 호출자(`RegionApi`)가 트랜잭션 밖에 있음이 증명된다 — 이 애노테이션은 그 자체가 실행되는 검증이다. 만약 기존 테스트가 깨진다면 그건 **가정이 이미 틀렸다는 발견**이므로 더 큰 소득이다.

---

### 별도 티켓 권장 항목 (DEFER)

#### [MAJOR-3 부분] `POST /v1/regions` 신규 생성 레이트리밋

**왜 나중에 해도 되는가**: ① JWT 인증 벽이 1차 방어로 이미 존재한다 — 익명 대량 유입 경로가 없다. ② `@Pattern`(FIX)이 문자 공간을 좁혀 무작위 문자열 생성을 차단한다. ③ 재사용 후보인 `RedisRateLimiterRepository`는 현재 `domain/running/infra/redis/`에 있고 일 단위 카운터 스크립트에 특화돼 있다 — 지역 등록에 쓰려면 global 또는 공용 위치로 승격 + 일반화가 필요하고, 이는 **이번 캐시키 교체와 무관한 인프라 리팩토링**이다(*"Make Quality a Requirements Issue" — Tip #8*). ④ 되돌림 자유도가 높다 — 레이트리밋은 언제든 추가·조정 가능하며 데이터를 남기지 않는다.

**티켓 조건**: 신규 생성(`findByName` 미스) 경로에만 적용. 기존 지역 조회는 무제한 유지(정상 트래픽 무영향). 착수 트리거 = `cache_puts_total{cache="course-map"}` 또는 `region` 행 수가 예상 상한(수천)을 넘는 것이 관측될 때.

#### [MINOR-1] MapStruct `id`→`regionId` 매핑 무검증

**왜 나중에 해도 되는가**: 올바른 해법은 이 매핑 1건에 테스트를 붙이는 것이 아니라 **MapStruct `unmappedTargetPolicy = ERROR`를 매퍼 전역에 적용**해 이름 불일치 매핑 누락을 컴파일 타임에 잡는 것이다. 그런데 이 정책 변경은 프로젝트의 모든 기존 매퍼에 영향을 미치므로 이번 브랜치(캐시키 교체) 범위를 명백히 벗어난다 — *"Take Small Steps — Always" — Tip #42*. 개별 매핑 테스트를 대신 넣는 것은 프로젝트의 "테스트는 핵심 로직만" 원칙에 어긋나는 망라식 대응이며, 근본 원인(정책 부재)을 남긴 채 증상 하나만 덮는다.

**티켓 조건**: 매퍼 전역 `unmappedTargetPolicy` 도입 티켓으로 분리. 현재 매핑은 동작 중이며 698건 그린이 회귀 감지 그물 역할을 한다.

#### [MINOR-2] 캐시 스탬피드 미대비 (`sync = true`)

**왜 나중에 해도 되는가**: 이것을 지금 고치는 것은 **관측 없는 최적화**다. 이번 사이클은 `enableStatistics()`로 히트율 실측 루프를 닫는 것 자체가 설계 목표(§4 관측)였다 — 그 데이터가 나오기 전에 `sync=true`를 켜면 시뮬레이션 예측(§2)과 실측을 비교하는 검증 루프에 교란 변수를 넣는 셈이다. 게다가 `sync=true`는 미스 시 락 대기를 유발해 지연 특성이 바뀌므로 **공짜가 아니다**. TTL 60초 + 리드모델 단일 쿼리라 미스 폭주가 곧바로 장애가 되지도 않는다(§9: 캐시 없이 운영하던 경로).

**티켓 조건**: 배포 후 `cache_gets_total{result="miss"}`의 60초 주기 톱니 패턴이 실제로 관측될 때 착수. 관측되지 않으면 이 티켓은 닫는다.

#### [MINOR-4] `CourseFacadeTest` 캐시 정리가 `@BeforeEach`가 아닌 테스트 본문

**왜 나중에 해도 되는가**: 되돌림 가능성 테스트를 통과한다 — `clearCourseMapCache()`(`CourseFacadeTest.java:404-409`)를 `@BeforeEach`로 승격하는 비용은 **지금이든 나중이든 동일**하다(#18). 현재 해당 테스트는 본문 첫 줄에서 정리를 수행하므로 자기 자신은 안전하고, 698건 그린이 유지되고 있다. 위험은 "다른 캐시 테스트가 추가될 때 순서 의존이 드러나는" 미래 시점에 발현된다.

**티켓 조건 (강제)**: 이번 FIX Task로 `CourseFacadeTest`에 캐시 관련 테스트가 **2건 추가된다**(CRITICAL-2, MAJOR-2). 이 두 건을 작성하는 순간 정리 로직을 공유해야 하므로, **그때 `@BeforeEach`로 승격한다** — 별도 티켓이 아니라 위 두 Task 수행 중 자연스럽게 해소하는 것이 총비용 최소다. 승격하지 않고 세 테스트가 각자 본문에서 정리를 호출하는 상태로 남기면, 그때는 DRY 위반(#15)으로 승격되어 FIX 대상이 된다.

---

### 넘어가도 되는 항목 (PASS)

#### [MINOR-3] Facade javadoc HTML 표가 설계 §5-2와 중복

**왜 괜찮은가**: 지적 자체는 타당한 DRY 위반(#15)이다 — 설계 문서가 바뀌면 이 표는 조용히 낡는다. 다만 **CRITICAL-2 Task가 §5-2를 바꾸므로 이 표는 그 작업에서 반드시 손대야 하고**, 해당 Task에 "표를 참조 한 줄로 축약"을 이미 포함시켰다. 독립 항목으로 따로 추적할 이유가 없다. 낡은 표를 고치는 대신 중복 자체를 제거하는 것이 옳은 방향이다.

#### [MINOR-5] 무관한 리팩토링(stream→for) 혼입

**왜 괜찮은가**: 세 가지 이유다. ① `CourseFacade.java:183-190`의 변경은 **동작이 정확히 동등**하다 — `containsKey` 후 `get`이 `get` 후 null 체크로 바뀐 것으로, 맵 조회 2회가 1회로 줄었을 뿐 결과가 같다. ② 되돌리는 것 자체가 새로운 diff를 만드는 순수 낭비다 — *"You Can't Write Perfect Software" — Tip #36*. ③ 커밋 위생 문제는 코드 문제가 아니라 **제출 절차 문제**이며, 이 프로젝트에는 이미 `/split-pr` 스킬이 있다. PR 제출 시 이 변경을 별도 커밋으로 분리하면 리뷰어의 인지 부하 문제는 해소된다. 코드를 되돌릴 이유는 없다.

---

### 작업 순서 (의존성)

1. **MAJOR-1 + MAJOR-3(@Pattern)을 같은 커밋에서** — NFD/NFC와 정규식 문자 범위가 상호작용한다. 따로 처리하면 iOS 등록이 전량 400으로 막히는 회귀가 숨는다.
2. **CRITICAL-1** — MAJOR-1 이후. 정규화된 이름이 확정된 뒤에 좌표 검증을 얹는다.
3. **CRITICAL-2 + MAJOR-2** — 둘 다 `CourseFacade`의 경로 분기를 건드리므로 함께. 이때 `CourseFacadeTest`의 캐시 정리를 `@BeforeEach`로 승격(MINOR-4 해소).
4. **MAJOR-4** — 독립. 언제 해도 무방하나 698건 그린 상태에서 단독 검증하는 것이 가장 깨끗하다.
5. **설계 문서 §3-2·§4·§5-1·§5-2·§5-3·§6-3 갱신을 마지막에 한 번에** — 코드가 확정된 뒤 문서를 맞춘다. CRITICAL 2건이 "설계의 빈틈"인 만큼, **문서를 고치지 않으면 다음 사람이 같은 구현을 다시 만든다.**

**FE 티켓 분리 여부**: 이번 판정 중 FE 계약 변경을 요구하는 항목은 **없다**. CRITICAL-2에서 404 유지를 택했다면 "404 → regionId 폐기 후 재요청" 계약을 FE 티켓으로 분리해야 했으나, 서버 폴백 강등을 택함으로써 FE는 설계 §7 그대로 진행한다. 이번 브랜치는 BE 범위로 완결된다.

---

### 실용주의 프로그래머의 한마디

> **완벽한 코드는 없다. 하지만 "지금이 아니면 비싸지는 것"과 "언제 해도 같은 것"은 구별해야 한다.**
>
> 이 리포트의 84점은 정당하다. 설계 일치도 10/10, 대표좌표 조회를 `@Cacheable` 안에 배치한 판단, 트랜잭션 없는 멱등 upsert — 전부 제대로 된 엔지니어링이다. 그런데도 MAJOR 4건이 모두 FIX로 올라간 이유는 코드가 나빠서가 아니라 **창이 닫히는 중이기 때문**이다. `region` 테이블에 첫 행이 들어가는 순간 이름 정규화는 3줄에서 마이그레이션이 되고, FE가 배포되는 순간 `radiusM` 가드는 1줄에서 사고 대응이 된다. 대표좌표는 애초에 불변으로 설계됐으니 오염되면 되돌릴 방법이 없다. *There Are No Final Decisions*는 "아무거나 나중에 고치면 된다"가 아니라 **"되돌릴 수 없는 것이 무엇인지 알고 그것만 먼저 못박아라"**는 말이다.
>
> CRITICAL-2는 다른 종류의 교훈이다. *Crash Early*는 훌륭한 원칙이지만 **복구 경로가 없을 때** 훌륭하다. 여기엔 완벽한 폴백이 이미 구현돼 있었고, 그런데도 404를 골랐다. 설계 §5-1이 "홈 화면을 막지 않는다"고 못박은 바로 그 문서의 한 섹션 뒤에서 말이다. 원칙끼리 충돌할 때는 **사용자에게 무엇이 보이는지**로 판정한다. dev 배포마다 QA 전원이 백지 화면을 보는 것은 "문제를 드러내는" 게 아니라 그냥 문제다.
>
> 나머지 6건은 넘어간다. 스탬피드 대비는 관측 데이터가 나오기 전엔 근거 없는 최적화이고, MapStruct 정책은 이번 PR이 감당할 범위가 아니며, stream→for는 되돌리는 것이 더 낭비다. **모든 지적을 고치는 것은 실용주의가 아니다.** 6건을 고치고 6건을 넘어가되, 넘어간 6건에 각각 "왜"와 "언제 다시 볼지"를 남겨두는 것 — 그게 실용주의다.
>
> 마지막으로: **문서를 반드시 함께 고쳐라.** 이번 CRITICAL 2건은 구현자가 설계를 어겨서 생긴 게 아니라 **설계를 충실히 따라서** 생겼다. 코드만 고치고 문서를 두면, 다음 사람이 문서를 읽고 같은 구멍을 다시 판다.
