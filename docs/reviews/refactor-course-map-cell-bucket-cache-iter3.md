# 코드 품질 리포트 — 셀 버킷(geohash p6) 캐시 전환 (iter3 / 수렴 확인)

- 대상 브랜치: `refactor/course-map-cell-bucket-cache`
- 설계 문서: `docs/design/course-cell-bucket-cache-design.md`
- 이전 리포트: `docs/reviews/refactor-course-map-cell-bucket-cache-iter1.md`, `docs/reviews/refactor-course-map-cell-bucket-cache-iter2.md`
- 빌드 상태(리뷰어가 직접 재현): `./gradlew test` **BUILD SUCCESSFUL / 138 클래스 724 tests / failures 0 / errors 0 / skipped 0**
- 3회차 범위: **Task 1~4의 실효성**(형식이 아니라 실제로 결함을 제거했는지) + **수정이 만든 새 결함** + **핵심 불변식 최종 재확인** + **머지 가능 여부 판단**

---

## 총평

3회차는 새로 볼 게 많지 않은 라운드였습니다. 그게 좋은 신호입니다.

**이번 라운드의 변경 표면이 정확히 5개 파일입니다.** `RedisConfig.java`, `RedisConfigTest.java`(신규),
`RunningCommandServiceTest.java`, `course-cell-bucket-cache-design.md`, 그리고 그 4개를 지지하는 문서 문단들.
`course` 도메인 코드(`CourseReadModelReader`, `CourseCellCache`, `GeoCell`, `CourseCellCacheEvictListener`)는
**한 줄도 건드리지 않았습니다.** 2회차에서 이미 초록이던 불변식 10개가 이번에 흔들릴 물리적 경로가 없다는 뜻입니다.
FIX 라운드에서 이걸 지키는 팀이 드뭅니다. 보통은 "고치는 김에" 옆 파일을 손대고, 그 옆 파일에서 회귀가 납니다.

그리고 이번엔 제가 **믿지 않고 직접 흔들어 봤습니다.** 2회차에서 여러분이 제 처방(Lettuce)을 실측으로 뒤집은 것과
같은 방법입니다. 테스트가 초록인 건 아무것도 증명하지 않습니다 — 그 테스트가 **깨질 수 있어야** 무언가를 지킵니다.
그래서 Task 2와 Task 4의 수정을 각각 되돌려 놓고 돌려봤습니다.

```
[뮤테이션 A] serverConfigOf(...) → if (!config.isSingleConfig()) return; 로 원복
  → RedisConfigTest "클러스터 구성에도 같은 값을 적용한다"
     AssertionFailedError: expected: 500 but was: 3000        ← Redisson 기본값이 그대로 남는 것까지 재현됨

[뮤테이션 B] RunningCommandService:184 eventPublisher.publishEvent(...) 주석 처리
  → RunningCommandServiceTest "…RunUpdatedEvent를 발행한다"
     Wanted but not invoked: publishEvent(RunUpdatedEvent[runId=11, courseId=30, …])

두 뮤테이션 모두 원복 후 재실행 그린. 전체 스위트 724/0 재확인.
```

**두 개 다 빨개졌습니다.** 이게 이번 라운드에서 제가 확인하고 싶었던 전부입니다. 2회차 MAJOR-3의 지적은
"CRITICAL 수정에 자동 회귀 가드가 없다"였는데, 지금은 가드가 있고 **그 가드가 실제로 문다는 것까지** 확인됐습니다.
0.0초짜리 순수 JUnit 테스트가 7.5초 스톨 회귀를 잡습니다. 비용 대비 가장 좋은 종류의 테스트입니다.

Task 2의 `serverConfigOf`도 컴파일만 되는 게 아니라 세 토폴로지에서 실제로 동작합니다.
테스트가 커버하지 않는 sentinel까지 제가 redisson 3.27.2 jar에 직접 물려 돌려봤고, masterSlave가
세 판별자 모두 false를 반환해 `error` 분기로 간다는 것도 확인했습니다(아래 §Task 2 상세).
와일드카드 캡처를 피하려고 setter 체이닝을 문장 4개로 분리한 것도 지시대로 됐고, 죽은 코드도 없습니다.

우려는 셋인데 전부 MINOR입니다. 그리고 셋 다 **"고친 것"이 아니라 "고친 것 주변의 여백"**에 있습니다.
지원 토폴로지 3개 중 sentinel 1개가 테스트 밖이고, 정작 위반 시 손해가 제일 큰 경로
(미지원 토폴로지에서 예외를 던지면 **앱이 아예 안 뜬다**)에 가드가 없고,
`application-prod.yml`이 `src/main/resources` 아래(=jar 안)에 있는데 문서는 "재배포 없이 조정 가능"이라고만 적혀 있어
**온콜이 레버를 어떻게 당기는지가 안 적혀 있습니다.** 셋 다 머지를 막을 이유는 아닙니다.

**93점입니다(1회차 85 → 2회차 89 → 3회차 93). 머지 차단 0건. 머지하십시오.**

---

## 총점: 93/100 — 등급 **S** (출시 준비 완료) · 2회차 89 → **+4**

---

## 차원별 점수

| 차원 | 1회차 | 2회차 | 3회차 | 핵심 피드백 |
|------|------|------|------|-------------|
| 가독성 | 9 | 9 | **9** | `serverConfigOf`가 "무엇을 하는가"를 이름으로 말한다. 감점 유지 사유는 커스터마이저 javadoc이 60줄 / 코드 14줄로, 설계 §8-2와 내용이 이중 관리된다는 점(드리프트 여지) |
| 아키텍처 준수 | 9 | 9 | **10** | 2회차 감점 사유였던 "설계 §5 변경 파일 목록에 `RedisConfig` 없음"이 해소(`:619`). 레이어·외부 API 불변 유지, `CourseApi` 변경은 javadoc 뿐 |
| 단일 책임 | 10 | 10 | **10** | 변동 없음. 토폴로지 판별이 `private static` 헬퍼로 분리되어 빈 정의가 더 얇아졌다 |
| 캡슐화 | 9 | 9 | **9** | 변동 없음. `serverConfigOf`가 `private static`이라 외부 노출 0 |
| 테스트 품질 | 8 | 9 | **9** | 뮤테이션 2건으로 가드가 실제로 문다는 것을 확인. 감점은 지원 토폴로지 3갈래 중 sentinel 미커버 + fail-safe(미지원 시 미전파) 무가드(MINOR-1·2) |
| 에러 처리 | 7 | 9 | **10** | 조용한 fail-open(`warn`)이 `error` + 3토폴로지 적용으로 대체됐고, 전역 영향(auth 5xx)이 코드·설계 문서 양쪽에 기록됐다 |
| 성능 | 8 | 8 | **8** | 변동 없음. DEFER 항목(셀 값 개수 상한) 유지 — 재지적 아님 |
| 보안 | 8 | 8 | **8** | 변동 없음. 신설 4xx 없음, 로그에 개인식별 정보 추가 없음 |
| 설계 일치도 | 9 | 9 | **10** | §5·§8-1·§8-2 **3곳 모두** 반영 확인(grep으로 교차 검증). 남은 갭은 §6 헤더의 "테스트 16건" 표기뿐(초과 달성 방향) |
| 유지보수성 | 8 | 9 | **10** | 안전 보증이 "단일 서버 구성일 때만 참"에서 "지원 토폴로지 전부에서 참, 아니면 error"로 바뀌어 조건이 사라졌다 |

---

## Task 1~4 실효성 판정

| # | 항목 | 판정 | 검증 방법(리뷰어 독립 확인) |
|---|------|------|------|
| Task 1 | 프로퍼티 키 이동 + javadoc 3문단 + 설계 문서 3곳 | ✅ **실효 확인** | grep 교차 검증 + 통합 테스트 컨텍스트 기동으로 플레이스홀더 해석 확인 |
| Task 2 | `serverConfigOf`로 3토폴로지 적용 + `error` 승격 | ✅ **실효 확인** | `javap`로 타입 계층 확인 + redisson jar에 직접 물려 sentinel/masterSlave 동작 실행 확인 |
| Task 3 | `RedisConfigTest` 신규 | ✅ **실효 확인 (회귀를 실제로 잡음)** | 뮤테이션 A로 cluster 케이스가 빨개지는 것 확인 |
| Task 4 | `RunUpdatedEvent` 발행 verify | ✅ **실효 확인 (회귀를 실제로 잡음)** | 뮤테이션 B로 빨개지는 것 확인 |

### Task 1 상세 — 새 프로퍼티 키가 실제로 읽히는가

**① 구 키 잔존 0건.**

```
$ grep -rn "spring.data.redis.retry" src/ docs/ | grep -v docs/reviews
→ 0건

$ grep -rn "ghostrunner.redis" src/ docs/ | grep -v docs/reviews
src/main/.../RedisConfig.java:58,87   (javadoc)
src/main/.../RedisConfig.java:95,96   (@Value)
docs/design/course-cell-bucket-cache-design.md:721  (§8-2 운영 체크리스트 8번)
```

문서와 코드가 **같은 키 문자열**을 갖고 있습니다. 2회차 MINOR-1의 지적(운영 문서에 못 박기 **전에** 이름을 고쳐야
비용이 싸다)이 순서대로 실행됐습니다.

**② 플레이스홀더가 실제로 해석된다.** 이건 눈으로 확인할 게 아니라 기동으로 확인할 문제인데, 이미 확인돼 있습니다 —
`RedisConfig`는 `@Configuration`이고 `redisTimeoutCustomizer`는 무조건 생성되는 빈입니다.
`IntegrationTestSupport`를 상속한 통합 테스트가 **전체 컨텍스트를 띄우므로**, `${ghostrunner.redis.retry-attempts:1}`에
오타가 있거나 `Duration` 변환이 실패하면 `BeanCreationException`으로 그 클래스들이 전부 빨개집니다.
**724건 그린이 이 키가 읽힌다는 증거**입니다. (별도 기동 테스트를 추가할 필요가 없다는 뜻이기도 합니다.)

**③ `timeout`/`connect-timeout`을 `spring.data.redis.*`에 남긴 판단이 옳다.** 다시 확인했습니다.
`RedissonAutoConfiguration.redisson()`이 `redisProperties.getTimeout()`/`getConnectTimeout()`을 먼저 반영하고
커스터마이저가 마지막에 덮으므로, 운영자가 yml로 조이면 **자동설정과 커스터마이저가 같은 값**을 씁니다.
반대로 재시도 2개를 같은 네임스페이스에 뒀다면 `RedisProperties`에 없는 키라 메타데이터상 "알 수 없는 속성"으로
표시됐을 겁니다. 두 그룹이 갈리는 게 정직하다는 판단이 유지됐고, **javadoc `:52-62`에 그 이유가 적혀 있습니다.**
다음에 "왜 키가 두 군데죠?"라고 묻는 사람에게 답이 이미 준비돼 있습니다.

**④ 설계 문서 3곳 모두 반영.**

| 위치 | 내용 | 확인 |
|---|---|---|
| §5 변경 파일(`:619`) | `RedisConfig`(+`redisTimeoutCustomizer` 신설 — **영향 반경이 course 도메인 밖**이라 §8-1·§8-2에 함께 기록) | ✅ |
| §8-1 리스크 수용표(`:705-706`) | `readOnly` 내 Redis I/O 수치 갱신(**장애 시 요청당 최대 ~2.9s(2왕복)**) + 재시도 축소 행 신설(중복 실행 위험 0 근거 포함) | ✅ |
| §8-2 운영 체크리스트 8번(`:718-727`) | 4개 키 + 롤백 방향 + **대가**(강등 발동 지연, DB 커넥션 점유 증가) + 네임스페이스가 갈린 이유 + `unsupported Redisson topology` error 로그 의미 | ✅ |

특히 §8-2에 **"부팅 로그에 `unsupported Redisson topology ... NOT applied` error가 보이면 이 값들이 하나도
적용되지 않은 것이다"** 를 적어 둔 게 좋습니다. Task 2가 만든 `error` 로그와 운영 문서가 연결됐습니다.
로그만 있고 문서에 그 로그의 의미가 없으면, 그 로그는 다음 사람에게 그냥 빨간 줄일 뿐입니다.

**⑤ MINOR-3(요청당 2왕복) 교정도 실효.** javadoc `:71-74`가 "1.45s는 **커맨드 1회** 기준, 요청 단위 상한은
최대 2왕복 ~2.9s, **HikariCP 풀 산정에는 이 값을 쓴다**"로 명시하고, 설계 §8-1 표의 수치와 일치합니다.
숫자가 두 곳에서 같습니다 — 이게 문서 갱신에서 제일 자주 틀리는 부분인데 맞췄습니다.

### Task 2 상세 — `serverConfigOf`가 세 토폴로지에서 실제로 동작하는가

타입만 맞고 런타임에 안 먹는 경우가 있어서, redisson 3.27.2 jar에 직접 물려 실행했습니다.

**① 타입 계층(javap)**

```
org.redisson.config.Config
  public boolean isSingleConfig() / isClusterConfig() / isSentinelConfig()      ← 3개 모두 public 존재
  public SingleServerConfig   useSingleServer()
  public ClusterServersConfig useClusterServers()
  public SentinelServersConfig useSentinelServers()

org.redisson.config.BaseConfig<T extends BaseConfig<T>>
  public T setTimeout(int) / setConnectTimeout(int) / setRetryAttempts(int) / setRetryInterval(int)
  public int getTimeout() / getConnectTimeout() / getRetryAttempts() / getRetryInterval()

SingleServerConfig            extends BaseConfig<SingleServerConfig>
ClusterServersConfig          extends BaseMasterSlaveServersConfig<ClusterServersConfig> extends BaseConfig
SentinelServersConfig         extends BaseMasterSlaveServersConfig<SentinelServersConfig> extends BaseConfig
```

**② 실행 확인(테스트가 커버하지 않는 sentinel + masterSlave)**

```
isSentinelConfig=true isSingle=false isCluster=false
timeout=500 connect=500 attempts=1 interval=200          ← 센티널에도 4개 값 전부 적용됨

masterSlave -> isSingle=false isCluster=false isSentinel=false   ← 세 판별자 모두 false → error 분기로 감
```

즉 **sentinel은 실제로 값이 적용되고, masterSlave/replicated는 예외를 던지지 않고 `error` 로그만 남기고 빠집니다.**
"기동을 깨뜨리지 않는다"는 이 코드의 fail-safe 계약이 성립합니다.

**③ 부작용 없음.** `serverConfigOf`는 판별자가 true인 모드에서만 대응 `use*Servers()`를 호출하므로,
**존재하지 않는 서버 설정을 새로 만들어내는 부작용이 없습니다.** (Redisson의 `use*Servers()`는 해당 필드가 null이면
새로 생성하는데, 여기서는 null이 아닐 때만 호출됩니다.) `useMasterSlaveServers()`/`useReplicatedServers()`를
추가하지 않은 것도 지시대로이고, 그 이유가 헬퍼 javadoc `:116-117`에 적혀 있습니다.

**④ `warn` → `error` 승격의 실효.** 2회차 지적의 핵심은 "보증이 깨졌는데 신호가 warn 한 줄"이었습니다.
지금 메시지는 **무엇이 깨졌는지(직행 강등 보증)와 어느 설계 결정인지(결정 2)까지** 담고 있습니다.
알림 룰이 error 레벨을 잡는다면 이건 사람에게 도달합니다.

### Task 3 상세 — 신규 테스트가 회귀를 실제로 잡는가

**① 뮤테이션으로 확인(위 총평 참조).** cluster 케이스가 `expected: 500 but was: 3000`으로 실패합니다.
`3000`이 Redisson 기본 응답 타임아웃이라는 것까지 실패 메시지가 말해줍니다 — **실패 메시지가 원인을 설명하는**
좋은 단언입니다.

**② 스코프 판단이 옳다.** 값 4개(500/500/1/200)를 테스트가 인자로 **주입**하고, `@Value` 기본값을 단언하지 않습니다.
장애 중에 온콜이 `ghostrunner.redis.retry-attempts=3`으로 올리는 것은 §8-2가 허용한 **정상 대응**인데,
기본값을 못 박는 테스트였다면 그 정상 대응이 CI를 빨갛게 만들었을 겁니다.
**고정한 것은 상수가 아니라 "주어진 값을 빠짐없이 쓴다"는 메커니즘**이고, 테스트 javadoc이 그 이유를 적고 있습니다.
이건 테스트를 잘 쓴 것보다 **테스트로 무엇을 지킬지 잘 고른 것**에 가깝습니다. 후자가 훨씬 어렵습니다.

**③ 비용.** `TEST-...RedisConfigTest.xml`의 `time="0.0"` — 스프링 컨텍스트가 뜨지 않습니다. 지시대로입니다.

### Task 4 상세

뮤테이션 B로 확인했고, 형태도 좋습니다. 새 테스트를 만들지 않고 **기존 `updateRunningPublicStatus_recalculatesCourse`에
단언을 얹었으며**, `@DisplayName`도 "…코스를 재계산하고, **지도 셀 이빅트용 RunUpdatedEvent를 발행한다**"로 갱신됐습니다.
`verify` 옆 주석이 `RunningCommandService:182-183` 주석을 역참조하는 것도 좋습니다 —
구경로 정리 PR에서 `publishEvent`를 지우려는 사람이 **테스트 실패 → 주석 → 코드 주석** 순으로 이유에 도달합니다.

이제 지도 캐시에 실질 의미가 있는 이빅트 발행 지점 **2곳(`publishCourseRunEvents:142`, `updateRunningPublicStatus:184`)이
모두 계약으로 고정**됐습니다. 2회차 MINOR-5가 닫혔습니다.

---

## 잘한 점 (3회차 신규)

### 1. FIX 라운드의 변경 표면을 5개 파일로 묶어둔 것

`course` 도메인 소스는 한 줄도 안 바뀌었습니다. 저는 이걸 리뷰에서 제일 먼저 봅니다.
FIX 라운드에서 회귀가 나는 전형적인 경로가 **"고치는 김에"** 이기 때문입니다.
2회차에서 초록이던 불변식 10개를 다시 전수 검증할 필요가 없어졌고, 저는 이번에 새로 만들어진 5개 파일에만
시간을 쓸 수 있었습니다. **리뷰 비용을 줄이는 것도 PR의 품질입니다.**

### 2. 3회 연속으로 "판정 기준"이 흔들리지 않은 것

2회차 판정이 1회차 판정과의 일관성에서 절반을 끌어왔고(“MINOR-2를 PASS한 근거를 새 4개 키에도 적용”),
이번 수정이 그 판정을 그대로 실행했습니다. DEFER 3건은 정말로 손대지 않았고, 명시 거부 3건
(RedissonClient 이중화 / RefreshTokenService 폴백 / 기본값 4개 못 박기)도 **하지 않은 채로 남아 있습니다.**

이게 왜 중요하냐면 — 리뷰 라운드가 반복되면 보통 **"지적된 건 일단 다 고치자"** 로 흐릅니다.
그러면 PR이 부풀고, 거부하기로 한 것까지 들어와서 리뷰 대상이 다시 커집니다.
**거부하기로 한 것을 거부한 채로 두는 규율**이 수렴을 만듭니다. 3회차에 새로 볼 게 적은 이유가 이겁니다.

### 3. 로그와 운영 문서를 이어붙인 것

`log.error("unsupported Redisson topology ... NOT applied ...")`와
§8-2의 "부팅 로그에 이 error가 보이면 이 값들이 하나도 적용되지 않은 것이다"가 짝을 이룹니다.
제가 겪은 장애 중 상당수는 **로그는 이미 찍히고 있었는데 아무도 그 로그의 의미를 몰랐던** 경우였습니다.
로그를 추가하는 사람과 그 로그를 볼 사람이 다르다는 걸 전제한 설계입니다.

---

## 개선 필요 사항

> **CRITICAL: 0건 · MAJOR: 0건 · MINOR: 3건.**
> **머지를 차단하는 항목은 없습니다.** 아래 3건은 전부 후속 티켓 대상입니다.

---

### [MINOR-1] 지원 토폴로지 3갈래 중 sentinel만 테스트 밖 — 테스트가 선언한 계약과 커버리지가 1갈래 어긋난다

**현재 코드** (`src/test/java/soma/ghostrunner/global/config/RedisConfigTest.java:14-21`):
```java
 * <p>값 자체(500/500/1/200)는 장애 중에 조정하는 <b>운영 레버</b>라 여기서 고정하지 않는다(설계 §8-2).
 * 고정하는 것은 <b>"주어진 값을 지원 토폴로지 전부에 빠짐없이 쓴다"</b>는 메커니즘이다 —
```

**문제점**:

테스트 클래스가 스스로 **"지원 토폴로지 전부"** 를 계약으로 선언했는데, 실제 케이스는 single·cluster 2개입니다.
`serverConfigOf`의 3번째 분기(`isSentinelConfig()` → `useSentinelServers()`)는 컴파일러만 보고 있습니다.

제가 실행해서 확인했으니(위 §Task 2 상세) **지금은 정상 동작합니다.** 그래서 MINOR입니다.
문제는 이 코드가 "복붙으로 3줄이 늘어난 형태"라는 점입니다 — 복붙 3줄에서 나는 사고는 항상 같은 모양입니다.

```java
if (config.isSentinelConfig()) return config.useClusterServers();   // ← 이런 오타
```

이건 컴파일도 되고, 반환 타입도 `BaseConfig<?>`라 맞고, single/cluster 테스트도 초록입니다.
그리고 센티널로 옮기는 날 `useClusterServers()`가 **없던 클러스터 설정을 새로 만들어** 값을 거기 쓰고,
정작 센티널 설정에는 아무것도 안 씁니다. 부팅은 됩니다. `error` 로그도 안 찍힙니다. 완전한 침묵입니다.

**개선 코드** — 기존 두 케이스와 같은 골격, 8줄:

```java
    @Test
    @DisplayName("타임아웃 커스터마이저는 센티널 구성에도 같은 값을 적용한다")
    void customizer_AppliesAllValues_ToSentinelServers() {
        // given
        Config config = new Config();
        config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("redis://127.0.0.1:26379");

        // when
        customize(config);

        // then
        SentinelServersConfig applied = config.useSentinelServers();
        assertThat(applied.getTimeout()).isEqualTo(500);
        assertThat(applied.getConnectTimeout()).isEqualTo(500);
        assertThat(applied.getRetryAttempts()).isEqualTo(1);
        assertThat(applied.getRetryInterval()).isEqualTo(200);
    }
```

(리뷰어가 위 구성 그대로 redisson 3.27.2에 물려 실행해 `timeout=500 connect=500 attempts=1 interval=200`을
확인했으므로, 이 테스트는 **현재 코드에서 통과합니다.** 붙이면 바로 초록입니다.)

**개선 이유**:

테스트가 선언한 계약과 실제 커버리지가 다르면, **그 차이만큼 테스트가 거짓말을 합니다.**
"지원 토폴로지 전부"라고 적힌 클래스를 본 다음 사람은 sentinel도 검증됐다고 믿습니다.
계약을 3갈래로 적었으면 케이스도 3개여야 합니다 — 아니면 javadoc을 "single·cluster 2종"으로 낮춰야 합니다.
**둘 중 무엇을 고쳐도 되지만, 어긋난 채로 두는 게 제일 나쁩니다.**

---

### [MINOR-2] 정작 위반의 손해가 가장 큰 경로(미지원 토폴로지에서 예외 미전파)에 가드가 없다

**현재 코드** (`src/main/java/soma/ghostrunner/global/config/RedisConfig.java:97-104`):
```java
        return config -> {
            BaseConfig<?> serverConfig = serverConfigOf(config);
            if (serverConfig == null) {
                // masterSlave/replicated 등 미지원 토폴로지 — 기동은 깨뜨리지 않되, 보증이 깨졌음을 크게 남긴다.
                log.error("RedisConfig - unsupported Redisson topology, Redis timeout/retry defaults NOT applied. "
                        + "지도 조회의 요청 단위 직행 강등 보증(설계 결정 2)이 성립하지 않는다. 구성을 확인할 것.");
                return;
            }
```

**문제점**:

이 `null` 분기는 성능 최적화도 예외 처리도 아니고, **"애플리케이션이 뜨지 않는 것"을 막는 fail-safe**입니다.
`RedissonAutoConfigurationCustomizer` 안에서 던진 예외는 `Redisson.create()` 이전에 터져 컨텍스트 기동을 깨뜨립니다.
즉 이 분기를 잘못 건드리면 손해가 **"타임아웃이 안 먹는다"가 아니라 "서비스가 안 뜬다"** 입니다.
이 파일에서 손해가 가장 큰 실패 모드인데, 여기만 테스트가 없습니다.

되돌아갈 수 있는 경로도 현실적입니다. 누군가 "null 반환은 지저분하다"며 이렇게 정리할 수 있습니다.

```java
// "어차피 세 가지밖에 없잖아" 라고 판단한 리팩터링
BaseConfig<?> serverConfig = config.isClusterConfig() ? config.useClusterServers() : config.useSingleServer();
```

single/cluster 테스트는 **둘 다 초록입니다.** 그리고 masterSlave 구성으로 뜨는 순간
`useSingleServer()`가 `IllegalStateException`을 던져 앱이 부팅에 실패합니다.
이건 지연 장애가 아니라 **즉시 전면 장애**입니다.

**개선 코드** — 3줄:

```java
    @Test
    @DisplayName("미지원 토폴로지에서는 예외를 던지지 않는다 — 값 미적용은 감수하되 기동은 깨뜨리지 않는다")
    void customizer_DoesNotThrow_OnUnsupportedTopology() {
        // given : masterSlave는 Config에 판별자가 없어 감지할 수 없는 구성이다
        Config config = new Config();
        config.useMasterSlaveServers().setMasterAddress("redis://127.0.0.1:6379");

        // when & then : 여기서 던지면 RedissonAutoConfiguration이 Config를 만드는 도중 터져 앱이 아예 뜨지 않는다
        assertThatCode(() -> customize(config)).doesNotThrowAnyException();
    }
```

**개선 이유**:

**테스트를 붙일 곳을 고르는 기준은 "코드가 복잡한 곳"이 아니라 "틀렸을 때 손해가 큰 곳"입니다.**
이 파일에서 그 기준으로 1등은 값 4개가 아니라 이 `null` 분기입니다.
그리고 이 테스트는 값이 적용되는지를 보지 않고 **"던지지 않는다"만** 봅니다 —
운영 레버를 잠그지 않으면서 fail-safe만 고정하는, Task 3이 이미 채택한 것과 같은 스코프 원칙입니다.

MINOR-1과 합치면 `RedisConfigTest`는 4케이스가 되고, 여전히 실행 시간 0.0초입니다.

---

### [MINOR-3] "재배포 없이 조정 가능"이라고 적힌 레버를, 온콜이 **어떻게** 당기는지가 없다

**현재 문서** (`docs/design/course-cell-bucket-cache-design.md:718-721`):
```
8. Redis 응답/재시도 기본값은 코드(`RedisConfig.redisTimeoutCustomizer`)에 있다. 재배포 없이 조정 가능:
   spring.data.redis.timeout(500ms) / spring.data.redis.connect-timeout(500ms)
   ghostrunner.redis.retry-attempts(1) / ghostrunner.redis.retry-interval(200ms)
```

**문제점**:

이 저장소에서 `application-prod.yml`의 위치는 `.gitignore:40-42` 기준으로 **`/src/main/resources/` 아래**입니다.
즉 **jar 안에 패키징되는 파일**입니다. 그렇다면 "재배포 없이 조정 가능"이 성립하는 경로는
yml 편집이 아니라 **외부 오버라이드**뿐입니다 — 환경변수(`GHOSTRUNNER_REDIS_RETRY_ATTEMPTS=3`),
커맨드라인 인자(`--ghostrunner.redis.retry-attempts=3`), 또는 jar 외부 `application.yml` 배치.

문제는 그 방법이 §8-2에도 javadoc에도 없다는 점입니다. 온콜이 새벽에 이 체크리스트를 읽고
"재배포 없이 조정 가능"만 보면, jar 안의 yml을 어떻게 고치나 고민하다 결국 **재배포를 합니다.**
그게 장애 중에 제일 하기 싫은 일입니다.

**개선 코드** — §8-2 8번에 두 줄:

```
   조정 방법(yml은 jar 안이라 편집 불가): 환경변수 또는 실행 인자로 오버라이드한다.
     GHOSTRUNNER_REDIS_RETRY_ATTEMPTS=3   (또는 --ghostrunner.redis.retry-attempts=3)
     SPRING_DATA_REDIS_TIMEOUT=3s         (또는 --spring.data.redis.timeout=3s)
   적용에는 프로세스 재기동이 필요하다(빌드/배포는 불필요).
```

**개선 이유**:

**레버는 "존재한다"가 아니라 "당길 수 있다"가 참이어야 레버입니다.**
2회차에서 이 4개 키를 문서로 끌어낸 이유가 "장애 중에 소스를 grep하게 만들지 말자"였는데,
지금은 grep 대신 **"이 값을 어디에 적지?"** 에서 막힙니다. 한 단계 덜 간 셈입니다.

덧붙여, "재배포 없이"라는 표현은 프로세스 재기동 없이(런타임 리프레시)로 읽힐 여지도 있습니다.
`@Value`는 빈 생성 시점에 한 번 해석되고 `RedissonClient`는 그때 만들어지므로 **재기동이 필요합니다.**
한 줄로 명시해 두면 "왜 값을 바꿨는데 안 먹지?"라는 두 번째 혼란도 같이 막힙니다.

> **참고(관찰, 지적 아님)** — 현재 `redisTimeoutCustomizer`의 javadoc은 60줄이고 코드는 14줄입니다.
> 내용의 가치는 높습니다(idempotency 근거, 잃는 것, 2왕복 상한). 다만 그중 상당 부분이 설계 §8-1·§8-2와
> **같은 사실을 두 곳에서** 서술하고 있어, 한쪽만 갱신되는 드리프트 여지가 생겼습니다.
> 지금은 두 곳이 일치합니다(수치·키 문자열 교차 확인 완료). 다음에 값을 조정할 때 **둘 다** 만지면 됩니다.

---

## 핵심 불변식 최종 재확인

> 3회차 변경 표면(5개 파일)에 `course` 도메인 소스가 없으므로, 1~6번은 2회차 검증 결과가 그대로 유효합니다.
> 그럼에도 회귀 여부를 코드로 재확인했습니다.

| # | 불변식 | 1회 | 2회 | 3회 | 3회차 근거 |
|---|--------|------|------|------|------|
| 1 | 넣은 곳 = 지울 곳 = 찾는 곳 | ✅ | ✅ | ✅ | `GeoCell` 사용처 전수(`grep`): 적재 `Reader:201` / 이빅트 `Listener:58,91` / 조회 `Reader:97` — 전부 동일 인코딩. `GeoCell.java` 3회차 무변경 |
| 2 | 원 ⊆ 박스 파리티 | ✅ | ✅ | ✅ | 캐시 경로(`Reader:103`)·직행(`Reader:117`) 모두 `withinRadius`로 종결. `GeoDistance`가 `BoundingBox` 상수를 참조(값 복제 0) |
| 3 | 강등 4갈래 수렴 | ✅ | ✅ | ✅ | `Reader:88, 91, 94, 101` 네 갈래 전부 `queryDirect`. degraded는 `candidatesOf` **진입 전** 차단(`:98-101`) |
| 4 | 적재 규칙(필터 앞·미스 셀만·LIMIT 시 전체 스킵) | ✅ | ✅ | ✅ | `fullHit()`이면 DB 미접근(`:147`), 미스 셀만 `fillMissedCells(:152,165)`, `cacheUnlessTruncated(:171,181)` 무변경 |
| 5 | 이빅트 커버리지(쓰기 7경로) | ✅ | ✅ | ✅ | 발행 7곳(`RunningCommandService:70,142,143,174,184,227` + `CourseService:93,128`) ↔ 리스너 3핸들러(`CourseMapDataChangedEvent`/`RunFinished`/`RunUpdated`)로 전부 수신. **실질 2곳은 이제 둘 다 계약 테스트 보유** |
| 6 | 외부 API 불변 | ✅ | ✅ | ✅ | `CourseApi` 3회차 diff는 **javadoc 뿐**(`regionId` 수용·미사용 명시). 시그니처·`CourseMapResponse` 무변경 |
| 7 | 동시성(AFTER_COMMIT · readOnly 내 I/O) | ⚠️ | ✅ | ✅ | 예외 미전파는 mock 테스트로 고정. `readOnly` 내 대기 상한 ~2.9s가 **이제 세 토폴로지에서 참** |
| 8 | 실패 모드 → 5xx 방지 | ⚠️ | ✅ | ✅ | 셀 캐시 직행 강등 유지. 셀 캐시 밖(auth·rate limit) 영향은 코드·설계 문서 양쪽에 기록됨 |
| 9 | 테스트 순서 독립성 | ⚠️ | ✅ | ✅ | 셀 키 생성 테스트 5개 전부 `@BeforeEach` 정리. 신규 `RedisConfigTest`는 외부 상태 0 |
| 10 | 죽은 코드 | ⚠️ | ✅ | ✅ | `serverConfigOf` 사용 1곳, `BaseConfig`·`Config` import 사용, `@Slf4j`의 `log`도 error 분기에서 사용. 미사용 심볼 0 |
| **11** | **가드가 실제로 문다(신규 확인)** | — | — | ✅ | **뮤테이션 2건 모두 재현 실패 확인**(Task 2 원복 → cluster 케이스 red / Task 4 발행 제거 → verify red) |

---

## 설계 문서 대비 차이 (3회차 잔여분)

| 항목 | 설계 | 구현 | 비고 |
|------|------|------|------|
| `RedisConfig` 변경 파일 등재 | §5 `:619` 등재됨 | 일치 | ✅ **2회차 MAJOR-1 해소** |
| Redis 재시도 축소 리스크 | §8-1 행 신설 | 일치 | ✅ **해소** |
| 4개 운영 키 | §8-2 8번 명시 | 코드 `@Value`와 키 문자열 일치 | ✅ **해소**. 조작 방법만 미기재 → MINOR-3 |
| 요청당 대기 상한 | §8-1 "장애 시 최대 ~2.9s(2왕복)" | javadoc `:71-74`와 동일 | ✅ **해소**(2회차 MINOR-3) |
| 지원 토폴로지 | §8-2 "single·cluster·sentinel만 지원" | `serverConfigOf` 3분기 | ✅ 일치. 테스트는 2/3 → MINOR-1 |
| §6 "테스트 설계 16건" | 16건 | 20건 상당(iter2 +2, iter3 +2) | 초과 달성 방향. 헤더 숫자만 낡음(무해) |
| `src/test/resources/application.yml` 플래그 명시 | §5 변경 대상 | 미반영 | 1·2회차 PASS 판정 유지 — 재지적 아님 |

---

## 2회차 대비 변화 요약

| 2회차 항목 | 2회차 판정 | 3회차 결과 |
|---|---|---|
| [MAJOR-1] 전역 재시도 축소 미문서화 (+MINOR-1 키, +MINOR-3 2왕복) | FIX | ✅ **해소** — 설계 §5·§8-1·§8-2 3곳 + javadoc 3문단, 키 문자열 교차 일치 |
| [MAJOR-2] `isSingleConfig()` fail-open | FIX | ✅ **해소** — 3토폴로지 적용 + `error` 승격. sentinel 실행 확인, masterSlave 미전파 확인 |
| [MAJOR-3] CRITICAL 수정에 회귀 가드 없음 | FIX | ✅ **해소** — `RedisConfigTest` 2건, **뮤테이션으로 실제 검출 확인**. 운영 레버(상수 4개)는 의도적으로 미고정 |
| [MINOR-5] 실질 이빅트 2곳 중 1곳만 계약 | FIX | ✅ **해소** — **뮤테이션으로 실제 검출 확인** |
| [MINOR-2] `candidatesOf` degraded 함정 | PASS | — 재지적 없음 |
| [MINOR-4] `connect-timeout` 완화 | DEFER | — 재지적 없음(티켓 유지) |
| 1회차 DEFER(셀 값 개수 상한, `evictions{ok}` 태그) | DEFER | — 재지적 없음(티켓 유지) |
| 명시 거부 3건(RedissonClient 이중화 / RefreshToken 폴백 / 기본값 못 박기) | 거부 | ✅ **거부 상태 유지됨** — PR이 부풀지 않았다 |

**신규 지적: CRITICAL 0 · MAJOR 0 · MINOR 3건.** 2회차 신규 8건(MAJOR 3 + MINOR 5) 대비 대폭 감소.
**세 라운드에 걸쳐 CRITICAL 1 → 0, MAJOR 3 → 0으로 수렴했습니다.**

---

## 머지 판단

### ✅ **머지 가능. 차단 항목 0건.**

| 구분 | 건수 | 항목 |
|---|---|---|
| 🚫 **머지 차단** | **0건** | 없음 |
| 📋 **후속 티켓** | **3건** | MINOR-1(sentinel 케이스 8줄), MINOR-2(미지원 토폴로지 미전파 가드 3줄), MINOR-3(§8-2 조작 방법 2줄) |
| 🎫 **기존 티켓 유지** | 3건 | 셀 값 개수 상한 / `evictions{ok}` 태그 분리 / `connect-timeout` 완화(dev 관측 후 결정) |

**차단하지 않는 근거**:

1. **MINOR-1·2는 현재 코드의 결함이 아니라 미래 편집에 대한 가드입니다.** sentinel 분기가 실제로 동작한다는 것을
   제가 실행으로 확인했고, 미지원 토폴로지 미전파도 코드상 자명합니다. **지금 배포되는 산출물의 동작은 옳습니다.**
2. **MINOR-3은 문서 두 줄이고, 이 PR이 만든 회귀가 아니라 이 PR이 새로 만든 레버의 사용법 누락**입니다.
   레버 자체는 존재하고 동작합니다(환경변수 relaxed binding).
3. 이 셋을 합쳐도 13줄이라, **원한다면 머지 전에 같이 넣어도 됩니다.** 어느 쪽이든 위험 차이는 없습니다.
   개인적으로는 MINOR-1·2(테스트 11줄)만 이번에 얹고 MINOR-3은 티켓으로 빼는 걸 권합니다 —
   테스트는 지금 파일이 열려 있을 때가 제일 싸고, 문서는 dev 배포 후 실제 조작을 한 번 해보고 적는 게 정확합니다.

**배포 후 1주 관측 항목**(2회차에서 넘어온 것):
- `lookups{degraded}` — 강등이 실제로 발동하는 빈도
- **auth 5xx 비율** — 재시도 축소(3→1)의 대가가 실제로 나타나는지. 늘면 §8-2대로 `retry-attempts`를 2~3으로
- `cells{hit} / cells{sum}` — 리플레이 예측 22%와 대조
- 부팅 로그에 `unsupported Redisson topology` **error가 없는지**(오탐 확인)

---

## 선배 개발자의 한마디

**첫째 — 이번 라운드의 진짜 성과는 코드가 아니라 "수렴했다"는 사실 자체입니다.**

CRITICAL 1건 → MAJOR 3건 → MINOR 3건. 라운드마다 지적의 **무게가 줄었고 개수도 줄었습니다.**
이게 당연해 보이지만 아닙니다. 제가 본 대부분의 리뷰 사이클은 2회차에서 PR이 부풀고, 3회차에서
"고치는 김에 들어온 것" 때문에 새 MAJOR가 납니다. 여기서 그러지 않은 이유는 하나입니다 —
**거부하기로 한 것을 끝까지 거부했기 때문입니다.** `RedissonClient` 이중화도, `RefreshTokenService` 폴백도,
`@Value` 기본값 못 박기도 여전히 안 들어와 있습니다. 그중 두 개는 "고치면 리뷰어가 좋아할 것 같은" 종류였는데도요.

**리뷰를 만족시키는 것과 문제를 푸는 것은 다릅니다.** 2회차에서 제 처방(Lettuce)이 틀렸다고 판정한 그 판단력이,
3회차에서는 "안 하기로 한 것을 안 하는" 형태로 나타났습니다. 같은 능력입니다.

**둘째 — 테스트를 붙일 곳은 "코드가 복잡한 곳"이 아니라 "틀렸을 때 손해가 큰 곳"입니다.**

이번에 남은 MINOR 두 개가 정확히 이 이야기입니다. `RedisConfigTest`는 값 4개가 적용되는지를 봅니다.
그런데 이 파일에서 틀렸을 때 손해가 제일 큰 건 값이 아니라 **`null` 분기**입니다.
값이 안 먹으면 "느린 Redis에서 강등이 늦게 걸린다"이고, `null` 분기가 깨지면 **"앱이 안 뜬다"** 입니다.
두 손해는 자릿수가 다릅니다.

저는 예전에 커넥션 풀 설정 클래스에 단위 테스트를 아주 꼼꼼하게 붙여둔 팀을 본 적이 있습니다.
maxPoolSize, idleTimeout, leakDetectionThreshold — 전부 단언돼 있었죠.
그런데 정작 "설정 파싱에 실패하면 기본값으로 뜬다"는 fail-safe에는 테스트가 없었고,
누군가 그 catch를 "죽은 코드"라며 걷어냈습니다. 배포 당일 새벽, 오타 하나로 전 인스턴스가 crash-loop에 빠졌습니다.
**테스트는 많았는데, 제일 중요한 한 줄만 없었습니다.**

3줄입니다. `assertThatCode(...).doesNotThrowAnyException()`.
지금 그 파일이 열려 있을 때 넣는 게 제일 쌉니다.

**셋째 — 레버는 "존재한다"가 아니라 "당길 수 있다"가 참이어야 합니다.**

§8-2에 4개 키를 적어둔 건 정말 잘한 일입니다. 그런데 그 yml이 jar 안에 있다는 사실까지 이어서 생각하면
한 발 더 남았습니다. 새벽 3시에 그 문서를 읽는 사람은 "조정 가능"이라는 단어만으로는 손을 못 움직입니다.
**문서의 독자는 그 문서를 쓴 사람이 아니라, 그 시스템을 처음 보는 사람이라고 가정해야 합니다.**

세 라운드 동안 이 PR은 "돌아가는 코드"에서 "운영에서 살아남는 코드"로 옮겨왔습니다.
장애 때 알아서 강등되고, 그 강등이 실제로 발동한다는 걸 실측했고, 그 실측이 테스트로 고정됐고,
그 대가가 문서에 적혔습니다. **머지하십시오. 좋은 PR입니다.**
