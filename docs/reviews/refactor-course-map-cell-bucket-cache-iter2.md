# 코드 품질 리포트 — 셀 버킷(geohash p6) 캐시 전환 (iter2 / 재리뷰)

- 대상 브랜치: `refactor/course-map-cell-bucket-cache`
- 설계 문서: `docs/design/course-cell-bucket-cache-design.md`
- 1회차 리포트: `docs/reviews/refactor-course-map-cell-bucket-cache-iter1.md`
- 빌드 상태: `./gradlew clean build -x sentryBundleSourcesJava` 그린 / **722 tests, 0 failures, 0 errors** (`build/test-results/test` 집계로 재확인)
- 재리뷰 범위: FIX 5건의 실효성 검증 + 수정 과정의 회귀·부작용 + 핵심 불변식 재확인 + 1회차 미발견 결함

---

## 총평

먼저, 이번 라운드에서 제일 잘한 건 코드가 아니라 **"리뷰어 말을 그대로 따르지 않은 것"** 입니다.

1회차 CRITICAL-1의 처방은 `LettuceClientConfigurationBuilderCustomizer`였습니다. 그대로 넣었으면
**컴파일도 되고 테스트도 초록인데 배포하면 아무 효과가 없는 코드**가 됐을 겁니다. 저도 이 저장소에
`spring-boot-starter-data-redis`가 있는 걸 보고 Lettuce라고 단정했습니다. 틀렸습니다.
제가 이번에 jar를 직접 뜯어 확인한 결과도 같습니다 —
`redisson-spring-boot-starter:3.27.2`의 `META-INF/.../AutoConfiguration.imports`에는
`RedissonAutoConfigurationV2` **하나뿐**이고, 그 부모 `RedissonAutoConfiguration`이
`redissonConnectionFactory` / `stringRedisTemplate` 빈을 직접 정의합니다.
즉 이 앱의 `RedisConnectionFactory`는 `RedissonConnectionFactory`이고,
`RefreshTokenService`·`CourseCellCache`·`PushIdempotencyService`·분산락이 **전부 같은 Redisson 클라이언트**를 씁니다.
Lettuce 커스터마이저는 no-op이었을 겁니다.

더 좋은 건 여기서 멈추지 않은 겁니다. 타임아웃만 500ms로 줄이고 "고쳤다"고 선언할 수도 있었는데,
**실측했더니 6,022ms / degraded=false**였다는 것. 그래서 재시도까지 파고들어 1,453ms / degraded=true를
만들어냈다는 것. 이건 *"고쳤다는 사실 자체도 검증 가능해야 한다"* 를 실제로 실행한 겁니다.
제가 `RedisExecutor` 바이트코드에서 확인한 문자열 상수가 그 실측을 뒷받침합니다 —
`"response timeout. new attempt {} for command {} ..."` 와
`"retry attempts, is non-idempotent command:"`. 즉 Redisson은 **응답 타임아웃 뒤에도 idempotent 커맨드는 재시도**하고
(그래서 MGET 대기 ≈ `(timeout + retryInterval) × (retryAttempts + 1)` = `(500+200)×2` = 1,400ms ≈ 실측 1,453ms — 산수가 맞습니다),
**non-idempotent 커맨드는 재시도하지 않습니다**. 이 두 번째 사실이 중요합니다 — 뒤에서 다시 말하겠습니다.

FIX 2~5도 전부 형식적 반영이 아니라 실효가 있습니다. 특히 FIX 5의
`handlers_DoNotPropagateEvictFailure`는 제가 제안한 것(좌표 null 3줄)보다 **한 단계 위**입니다.
mock으로 `evict`가 예외를 던지게 만들어 "AFTER_COMMIT은 절대 던지지 않는다"를 **핸들러 두 개 모두**에 대해
못 박았으니, 누가 try/catch를 "죽은 코드"라 판단해 걷어내면 그 순간 빨개집니다. 제안보다 나은 구현입니다.

이제 우려입니다. 이번엔 **전부 한 곳에 몰려 있습니다 — `RedisConfig.redisTimeoutCustomizer` 빈 하나.**

이 빈은 셀 캐시를 위해 만들어졌지만 영향권은 **인증·처리율 제한·푸시 멱등·구경로 캐시 전부**입니다.
그런데 강등 경로를 가진 소비자는 셀 캐시 하나뿐입니다. 나머지 셋은 예외가 그대로 5xx가 됩니다.
"이전에도 5xx였고 6초 걸리던 게 1.4초로 줄었을 뿐"이라는 반론은 대체로 맞습니다 —
하지만 `retryAttempts: 3 → 1`은 시간만 줄인 게 아니라 **"보낼 수 없는 커맨드"의 흡수 창을 4.5초에서 0.2초로 줄인**
변경이고, 이건 SET/DEL/EVAL 같은 non-idempotent 커맨드(= 로그인 토큰 저장, 레이트리밋 Lua)에서
**유일하게 존재하던 재시도 안전망**이었습니다. 이 트레이드가 나쁘다는 게 아니라, **`RedisConfig` javadoc 안에만 적혀 있고
설계 문서 §8-1 리스크 표에도 §8-2 운영 체크리스트에도 없다**는 게 문제입니다.
새벽에 로그인 5xx 스파이크를 보는 사람은 "코스 지도 캐시 PR"을 의심하지 않습니다.

두 번째는 아이러니입니다. 1회차의 결론이 *"지식은 주석이 아니라 실행되는 것에 담아라"* 였고 FIX 3·5가 정확히 그걸 했는데,
**정작 CRITICAL 수정 자신에게는 자동 회귀 가드가 하나도 없습니다.** 값 4개가 전부 코드 기본값이고,
`isSingleConfig()`가 아니면 **warn 로그 한 줄 남기고 조용히 아무것도 적용하지 않습니다.**
클러스터로 옮기는 날 CRITICAL-1이 그대로 부활하는데, 신호는 부팅 로그 한 줄뿐입니다.

점수는 **89점**입니다(1회차 85). CRITICAL은 닫혔고 남은 건 전부 "고친 것을 지키는 장치"입니다.
머지해도 좋되, MAJOR-2(3줄)와 MAJOR-3(5줄)은 같이 넣고 나가시길 권합니다.

---

## 총점: 89/100 — 등급 **A** (약간의 개선 후 출시 가능) · 1회차 85 → **+4**

---

## 차원별 점수

| 차원 | 1회차 | 2회차 | 핵심 피드백 |
|------|------|------|-------------|
| 가독성 | 9 | **9** | `fullHit()` 도입으로 조회 분기 의도가 선명해졌고 발행부 주석 4곳이 사실과 맞다. `candidatesOf`가 degraded를 다루는 것처럼 읽히는 것만 남음(MINOR-2) |
| 아키텍처 준수 | 9 | **9** | 레이어·외부 API 불변 유지. `RedisConfig`가 global/config에 있는 것도 정합. 설계 §5 "변경 파일"에 `RedisConfig`가 없다는 문서 갭만 존재 |
| 단일 책임 | 10 | **10** | 변동 없음. 커스터마이저 빈도 `RedisConfig` 안에 응집 |
| 캡슐화 | 9 | **9** | 변동 없음. `CellCacheLookup`이 이제 3개 public API를 전부 실제로 쓴다 |
| 테스트 품질 | 8 | **9** | 순서 의존 제거(FIX 2), [R3] 2건(FIX 5), 발행 계약 1건(FIX 3). 남은 것: 실질 이빅트 발행 2곳 중 1곳만 커버(MINOR-5), 타임아웃 구성 무가드(MAJOR-3) |
| 에러 처리 | 7 | **9** | **CRITICAL 해소.** 강등이 "느린 Redis"에서 실제로 발동함을 실측으로 확인. 감점은 전역 재시도 축소가 강등 경로 없는 3개 소비자에 미치는 영향이 미문서화(MAJOR-1) |
| 성능 | 8 | **8** | 요청당 Redis 대기 상한이 60s → 실질 1.4s(최대 2왕복 ≈ 2.9s)로 확정. MAJOR-1(셀 값 상한)은 DEFER 유지 |
| 보안 | 8 | **8** | 변동 없음. 신설 4xx 없음, 로그에 개인식별 정보 추가 없음 |
| 설계 일치도 | 9 | **9** | `fullHit()` 사용으로 §3-4 공개 API 목록이 참이 됨. 대신 §8-1·§8-2에 Redis 타임아웃/재시도 결정이 미반영(신규 갭) |
| 유지보수성 | 8 | **9** | 낡은 주석 4곳 제거로 "구경로 삭제 사고" 경로가 닫힘. 감점은 `isSingleConfig()` fail-open(MAJOR-2) |

---

## FIX 5건 실효성 판정

| # | 항목 | 판정 | 근거 |
|---|------|------|------|
| FIX 1 | Redis 커맨드 타임아웃 | ✅ **해소 (전제 교정 포함)** | 커스터마이저 종류·적용 순서·재시도 산수 모두 독립 검증. 아래 상세 |
| FIX 2 | 파리티 테스트 셀 키 정리 | ✅ **해소** | `course:*` + `course-cells*` 둘 다 삭제. 셀 캐시를 만지는 테스트 클래스 **5개 전부**가 이제 `@BeforeEach` 정리 보유 |
| FIX 3 | 발행부 주석 + 계약 테스트 | ✅ **해소 (부분 커버)** | 주석 4곳 사실 일치. 계약 테스트는 **실질 발행 2곳 중 1곳** 커버 → MINOR-5 |
| FIX 4 | `fullHit()` 사용 | ✅ **해소 (동치)** | 논리 동치 증명 아래. degraded 입력 오용 여지만 MINOR-2 |
| FIX 5 | [R3] 테스트 2건 | ✅ **해소 (제안 이상)** | 좌표 null no-op + **예외 미전파를 mock으로 강제** — try/catch 삭제가 실제로 빨개진다 |

### FIX 1 — 독립 검증 결과

**① "Lettuce가 아니라 Redisson" 전제 교정이 옳다.** 제가 jar를 직접 확인했습니다.

```
redisson-spring-boot-starter-3.27.2.jar
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    → org.redisson.spring.starter.RedissonAutoConfigurationV2   (유일)

RedissonAutoConfiguration (V2의 부모) 이 정의하는 빈
    → redissonConnectionFactory(RedissonClient)
    → stringRedisTemplate(RedisConnectionFactory)
    → redisTemplate(RedisConnectionFactory)
    → redisson()
```

`RedisConnectionFactory` 빈이 Redisson 쪽에서 나오므로 Spring Boot의 `LettuceConnectionConfiguration`
(`@ConditionalOnMissingBean(RedisConnectionFactory.class)`)이 통째로 건너뛰어집니다.
**1회차 리포트의 처방을 그대로 따랐다면 "고쳤다고 믿지만 배포 산출물에는 효과가 없는" 최악의 상태가 됐습니다.**
실측으로 이걸 잡아낸 판단이 이번 라운드에서 가장 값집니다.

**② 커스터마이저가 프로퍼티를 이긴다.** `RedissonAutoConfiguration.redisson()` 바이트코드 확인 결과, 순서가

```
Config config = new Config();
config.useSingleServer()
      .setAddress(...).setDatabase(...).setUsername(...).setPassword(...).setClientName(...)
      .setConnectTimeout(redisProperties.getConnectTimeout())   // ← 프로퍼티 반영
      .setTimeout(redisProperties.getTimeout());
...
for (RedissonAutoConfigurationCustomizer c : customizers) c.customize(config);   // ← 마지막
return Redisson.create(config);
```

입니다. javadoc의 "프로퍼티 반영 **이후** 마지막에 적용된다"는 서술이 정확합니다.
`@Value`가 같은 키(`spring.data.redis.timeout` / `connect-timeout`)를 읽으므로 운영자가 yml로 조이면
자동설정과 커스터마이저가 **같은 값**을 쓰게 되는 것도 정합적입니다. 잘 설계했습니다.

**③ 재시도 산수가 맞다.** `BaseConfig` 바이트코드에서 확인한 Redisson 3.27.2 기본값:

| 항목 | Redisson 기본 | 이 PR |
|---|---|---|
| `timeout` (응답) | 3,000ms | 500ms |
| `connectTimeout` | 10,000ms | 500ms |
| `retryAttempts` | 3 | 1 |
| `retryInterval` | 1,500ms | 200ms |

`RedisExecutor`가 가진 로그 문자열이 동작을 말해줍니다.

- `"response timeout. new attempt {} for command {} and params {} node {}"`
  → **응답 타임아웃 후에도 재시도한다**(그래서 타임아웃만 줄여선 강등이 안 걸렸던 것).
- `"retry attempts, is non-idempotent command:"`
  → **non-idempotent 커맨드는 재시도하지 않고 바로 실패시킨다.**

따라서 idempotent인 MGET의 호출자 대기는 `(timeout + retryInterval) × (retryAttempts + 1)`
= `(500 + 200) × 2` = **1,400ms** ≈ 실측 1,453ms. 산수가 실측과 맞습니다.
javadoc의 "`retryInterval × retryAttempts + timeout`"은 근사 표현이라 살짝 부정확하지만 결론은 같습니다.

**④ 부작용 검증 — 다른 Redis 사용처 (재리뷰 요청 항목)**

같은 클라이언트를 쓰는 소비자와 실패 시 결과를 전수 확인했습니다.

| 소비자 | 커맨드 | 예외 시 결과 | 흡수/폴백 |
|---|---|---|---|
| `CourseCellCache` (지도) | MGET / 파이프라인 SET / DEL | **직행 강등** | ✅ 있음 |
| `RefreshTokenService` (로그인·재발급·로그아웃) | SET / GET / DEL | `AuthService`까지 그대로 전파 → **5xx** | ❌ 없음 |
| `PacemakerRateLimitService` | EVAL(Lua) | `incrementRateLimitCounter`에서 전파 → **5xx** | ❌ 증가 경로 없음 (감소만 `@Retryable`) |
| `PushIdempotencyService` | GET / SETNX / SET / DEL | `catch (Exception)` → `LOCK_ACQUIRED` **fail-open** | ✅ 있음 |
| `CourseCacheRepository` (구경로) | — | 구경로 전용, 신경로 미사용 | — |
| 분산락(`RedissonClient`) | — | **`getLock`만 노출, 실제 획득 코드 0건** (grep 확인) | 해당 없음 |

정리하면:

- **레이트리밋 오작동(중복 차감) 우려는 근거 없음.** Redisson이 non-idempotent 커맨드를 응답 타임아웃 후
  재시도하지 않으므로, 재시도 축소가 Lua 이중 실행을 만들 수 없습니다. 오히려 **재시도 자체가 원래 없던** 영역입니다.
  `RedisConfig` javadoc이 "전부 단일 고속 커맨드"라고만 적고 있는데, 이 idempotent/non-idempotent 구분이
  안전성의 진짜 근거이므로 거기 적어두면 다음 사람이 같은 조사를 반복하지 않습니다.
- **인증 실패 여지는 실재하되, 방향은 "더 빨리 실패"다.** 순간 블립에서 이전에도 결과는 5xx였고
  시간만 ~7.5s → ~1.4s로 줄었습니다. **다만 `retryAttempts × retryInterval`(= "아직 보내지 못한 커맨드"의 흡수 창)이
  4,500ms → 200ms로 줄어든 것은 시간 단축이 아니라 내성 감소입니다.** TCP 재전송(RTO 최소 200ms) 한 번,
  또는 커넥션 재수립 중인 짧은 구간이 여기 걸립니다. 이건 MAJOR-1로 별도 기술합니다.

---

### FIX 4 — 로직 동치성 검증

`CourseReadModelReader.findCoursesForMap`이 `candidatesOf` **호출 전에** `if (lookup.degraded()) return queryDirect(...)`로
빠져나가므로, `candidatesOf` 진입 시점의 `degraded`는 **항상 false**입니다. 따라서

```
fullHit()  ≡  !degraded && missedCells.isEmpty()  ≡  missedCells.isEmpty()   (진입 시점 한정)
```

로 이전 판정과 동치입니다. 반환값도 확인했습니다 — full-hit 분기는 `classify`가 만든
`List.copyOf(cachedCourses)`(불변)를 그대로 돌려주는데, 유일한 소비자 `withinRadius`가 `stream().filter().toList()`만
하므로 변형이 없습니다. `covering.isEmpty()` → `CellCacheLookup.empty()` 경로도 `fullHit()==true` → `List.of()` 반환으로
이전과 같습니다. **동치 성립.** 가독성은 개선됐습니다 — "전 셀 히트면 DB를 아예 안 만진다"가 한 줄로 읽힙니다.

---

## 잘한 점 (이번 라운드 신규)

### 1. 리뷰어의 처방을 검증하고 **틀렸다고 판정한 것**

`RedissonAutoConfigurationV2` → `RedisConnectionFactory` 대체 사실은 코드 어디에도 안 적혀 있고,
`build.gradle`에 `spring-boot-starter-data-redis`가 있으니 Lettuce라고 믿는 게 자연스럽습니다.
그런데 **믿지 않고 실측했습니다.** 그리고 실측 결과가 예상과 다르자(6,022ms / degraded=false)
"타임아웃을 넣었으니 됐다"로 끝내지 않고 재시도까지 파고들었습니다.

이건 *Don't Program by Coincidence*의 정석입니다. 제가 40분을 태웠던 그 장애도,
대시보드가 "Redis UP"이라고 말하는 걸 믿었기 때문이었습니다. **믿을 수 있는 것에만 의존한다**는 원칙을
리뷰어의 리포트에도 적용한 겁니다. 이번 PR 전체에서 제일 값진 판단입니다.

### 2. `handlers_DoNotPropagateEvictFailure` — 제안보다 나은 테스트

제가 요청한 건 "좌표 null이면 no-op" 3줄이었습니다. 구현은 거기서 한 걸음 더 나갔습니다.

```java
CourseCellCache failingCache = mock(CourseCellCache.class);
doThrow(new RuntimeException("redis down")).when(failingCache).evict(any(GeoCell.class));
CourseCellCacheEvictListener listenerWithFailingCache =
        new CourseCellCacheEvictListener(readModelRepository, failingCache, metrics);
```

이게 왜 더 나은가 — 좌표 null 테스트만 있으면 "이 catch는 null 가드용이구나"로 읽힙니다.
그러면 누가 `evict` 앞에 null 체크를 옮겨놓고 catch를 걷어내도 초록입니다.
반면 이 테스트는 **`evict`가 던진다는 가정 자체**를 고정하므로 catch 없이는 통과할 수 없습니다.
`CourseCellCache.evict`가 이미 예외를 흡수하는데도 catch를 유지하는 이유가 **테스트로 설명**됩니다.
"커밋 성공 후 500"은 제가 본 것 중 사용자 신뢰를 가장 빨리 무너뜨리는 실패 유형입니다.
결제가 됐는데 화면엔 실패라고 뜨는 그것 말이죠. 이걸 기계가 지키게 만든 게 맞습니다.

### 3. 테스트 정리 규율이 **예외 없이** 복원된 것

셀 캐시 키를 만드는 테스트 클래스 5개(`CourseCellCacheTest`, `CourseCellCacheEvictListenerTest`,
`CourseReadModelReaderTest`, `CourseFacadeTest`, `CourseMapPathParityTest`)가 **전부** `@BeforeEach`에서 정리합니다.
`CourseMapPathParityTest`의 주석이 특히 좋습니다 —

> `"course:*"` 패턴은 `course-cells::...`를 매칭하지 않는다(7번째 문자가 `:`가 아니라 `-`).

이게 원인 진단입니다. "왜 예전엔 안 지워졌나"가 문자 하나 수준으로 적혀 있으니,
다음에 캐시 이름을 바꾸는 사람이 같은 함정에 빠지지 않습니다. 규율의 예외가 하나 있으면 규율이 아닌데,
그 예외가 사라졌습니다.

---

## 개선 필요 사항

> **CRITICAL: 0건.** 1회차 CRITICAL-1은 해소됐고, 신규 CRITICAL은 없습니다.

---

### [MAJOR-1] 전역 재시도 축소가 강등 경로 없는 3개 도메인의 실패 의미를 바꿨는데, 설계 문서 어디에도 없다

**현재 코드** (`src/main/java/soma/ghostrunner/global/config/RedisConfig.java:44-46`):
```java
 * <p>이 설정의 영향권은 셀 캐시·구경로 코스 캐시·리프레시 토큰·처리율 제한 Lua로, 전부 <b>단일 고속 커맨드</b>다.
 * 분산락도 같은 클라이언트를 쓰지만 현재 락을 실제로 획득하는 코드는 없다({@code getLock}만 노출).
 * 블로킹 커맨드를 도입한다면 이 기본값을 다시 검토해야 한다.</p>
```

**현재 코드** (`src/main/java/soma/ghostrunner/domain/auth/application/RefreshTokenService.java:22-25`):
```java
    public void saveToken(String memberUuid, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + memberUuid;
        redisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL_MILLIS, TimeUnit.MILLISECONDS);
    }   // ← try/catch 없음. 예외는 AuthService:58,94 를 지나 그대로 5xx
```

**문제점**:

javadoc이 정확히 서술한 것은 "**단일 고속 커맨드라 타임아웃 단축이 안전하다**"까지입니다. 맞습니다.
하지만 이 커스터마이저가 바꾼 값은 타임아웃만이 아닙니다. **`retryAttempts: 3 → 1`, `retryInterval: 1500 → 200ms`** 도 함께 바뀌었고,
이건 지연 상한이 아니라 **"아직 전송하지 못한 커맨드"의 흡수 창**을 바꿉니다.

```
                       흡수 창 (retryInterval × retryAttempts)
  변경 전:  1,500ms × 3 = 4,500ms
  변경 후:    200ms × 1 =   200ms      ← 22.5배 축소
```

여기 걸리는 현실적인 사건은 이렇습니다.

- TCP 재전송 — 리눅스 최소 RTO가 200ms입니다. 패킷 하나가 떨어지면 재전송까지 200ms~1s.
- Redis 프로세스 재시작 / 컨테이너 롤링 — 커넥션 재수립에 수백 ms.
- ElastiCache 노드 교체 직후 커넥션 풀 재구성 구간.

이전에는 이 창을 Redisson이 조용히 삼켰습니다. 이제는 예외가 됩니다. 그리고 이 예외를 **처리할 수 있는 소비자는
셀 캐시 하나뿐**입니다. 나머지는 아래처럼 흐릅니다.

```
RefreshTokenService.saveToken  →  AuthService:58 (로그인) / :94 (재발급)  →  5xx
PacemakerRateLimitService.incrementRateLimitCounter  →  5xx (증가 경로엔 @Retryable 없음)
```

오해 없도록 덧붙이면, 저는 이 트레이드가 **틀렸다고 보지 않습니다.** 6초 기다렸다 5xx 주는 것보다
1.4초에 5xx 주는 게 낫고, 스레드·커넥션 점유 관점에서는 압도적으로 낫습니다.
문제는 **이 결정이 `RedisConfig` javadoc 안에만 있다**는 겁니다.

- 설계 문서 §8-1 리스크 수용표 — Redis 타임아웃/재시도 항목 **없음**
- 설계 문서 §8-2 운영 체크리스트 — **없음** (1~7번 어디에도)
- 설계 문서 §5 변경 파일 목록 — `RedisConfig` **없음**

배포 후 로그인 5xx가 늘었을 때, 온콜은 "코스 지도 캐시 PR"을 의심하지 않습니다.
**변경의 영향 반경이 변경의 이름과 다르면, 그건 문서로 이어줘야 합니다.**

**개선 코드** — ① javadoc에 안전 근거의 핵심(idempotency)을 적고, ② 설계 문서에 결정과 관측 지점을 남긴다.

```java
    /**
     * ... (기존 서술 유지) ...
     *
     * <p><b>재시도 축소가 안전한 이유</b> — Redisson은 응답 타임아웃 뒤 <b>idempotent 커맨드만</b> 재시도하고,
     * non-idempotent 커맨드(SET·DEL·EVAL)는 재시도 없이 실패시킨다({@code RedisExecutor}:
     * "retry attempts, is non-idempotent command"). 따라서 재시도 축소가 리프레시 토큰 저장이나
     * 처리율 제한 Lua의 <b>중복 실행을 만들 수 없다.</b></p>
     *
     * <p><b>대신 무엇을 잃는가</b> — {@code retryInterval × retryAttempts}(= "아직 전송하지 못한 커맨드"의
     * 흡수 창)가 4,500ms → 200ms로 줄었다. TCP 재전송(RTO 최소 200ms)이나 커넥션 재수립 구간이 여기 걸리면,
     * 강등 경로가 없는 소비자({@code RefreshTokenService}, {@code PacemakerRateLimitService})는
     * 그 요청이 5xx가 된다. 변경 전에도 결과는 5xx였고 소요가 ~7.5s → ~1.4s로 줄었을 뿐이지만,
     * <b>빈도는 늘 수 있다.</b> 배포 후 auth 5xx 비율을 함께 관측한다.</p>
     */
```

설계 문서 `§8-1`에 한 줄:

| 항목 | 손해 상한 | 수용 근거 |
|---|---|---|
| Redis 재시도 축소(3→1) | 순간 블립 시 로그인·페이스메이커 요청 5xx | 이전에도 결과는 5xx였고 소요만 7.5s→1.4s. non-idempotent 재시도가 없어 중복 실행 위험은 0. 관측: auth 5xx 비율 |

`§8-2` 운영 체크리스트에 한 줄:

```
8. Redis 응답/재시도 기본값은 코드(RedisConfig)에 있다 —
   spring.data.redis.{timeout, connect-timeout, retry-attempts, retry-interval}로 재배포 없이 조정.
   Redis 블립 시 로그인 5xx가 늘면 retry-attempts를 2~3으로 되돌린다(대가: 강등 발동이 그만큼 늦어짐).
```

**개선 이유**:

롤백 레버가 코드 안에만 있으면, 장애 중에 그 레버를 찾을 사람이 소스를 grep해야 합니다.
1회차에서 `course.cache.cell-bucket.enabled`가 yml에 없는 것을 PASS로 넘긴 근거가
"설계 문서 운영 체크리스트에 키가 명시되어 있다"였습니다. **이번에 추가된 4개 키에는 그 근거가 없습니다.**
같은 기준을 적용하면 문서 한 줄이 필요합니다.

그리고 idempotency 근거를 적어두는 것은 단순한 친절이 아닙니다 — 다음에 누가
`retry-attempts`를 더 만지려 할 때 "Lua가 두 번 돌면 어쩌지"에서 반나절 조사를 다시 하게 됩니다.
**한 번 확인한 사실은 코드 옆에 둡니다.**

---

### [MAJOR-2] `isSingleConfig()` 가드가 조용히 fail-open이라, 클러스터 전환 날 CRITICAL-1이 warn 로그 한 줄로 부활한다

**현재 코드** (`src/main/java/soma/ghostrunner/global/config/RedisConfig.java:64-75`):
```java
        return config -> {
            if (!config.isSingleConfig()) {
                // 클러스터/센티널 구성에서 useSingleServer()는 예외를 던진다 — 기동을 깨뜨리느니 기본값을 둔다.
                log.warn("RedisConfig - not a single-server Redisson config, timeout defaults not applied");
                return;
            }
            config.useSingleServer()
                    .setTimeout((int) commandTimeout.toMillis())
                    .setConnectTimeout((int) connectTimeout.toMillis())
                    .setRetryAttempts(retryAttempts)
                    .setRetryInterval((int) retryInterval.toMillis());
        };
```

**문제점**:

가드의 판단 자체는 맞습니다 — `Config.useSingleServer()`는 `clusterServersConfig`/`sentinelServersConfig`가
이미 설정돼 있으면 `IllegalStateException`을 던지고, 그걸 커스터마이저 안에서 던지면 **애플리케이션이 부팅되지 않습니다.**
기동을 깨뜨리지 않기로 한 판단은 옳습니다.

문제는 **그 다음에 일어나는 일**입니다. 이 PR이 내건 안전 보증은
"Redis 장애 시 요청 단위 직행 강등"이고, 그 보증은 **타임아웃·재시도 설정에 100% 의존합니다.**
클러스터/센티널로 옮기는 순간 이 4개 값이 Redisson 기본값(응답 3s / 연결 10s / 재시도 3회 × 1.5s)으로 돌아가고,
**호출자 대기가 다시 ~7.5초가 됩니다.** 그러면:

```
CourseFacade.findCoursesByPosition   @Transactional(readOnly = true)   ← DB 커넥션 획득
  └ CourseCellCache.lookup → multiGet(...)                            ← 다시 최대 ~7.5초 블로킹
```

즉 1회차 CRITICAL-1이 그대로 부활합니다. 그런데 남는 신호는 **부팅 시점 warn 로그 한 줄**뿐입니다.
부팅 로그는 아무도 안 봅니다. 그리고 인프라 마이그레이션 당일에는 로그가 수천 줄 쏟아집니다.

게다가 클러스터 전환은 가정이 아닙니다. 설계 §8-2 운영 체크리스트 1번이
"셀 키 수가 기존보다 많다(24,028 vs 5,245) — `maxmemory-policy` 확인"이라고 적고 있습니다.
**이 PR이 키 공간을 5배로 늘렸으므로, 스케일업 압력을 만든 것도 이 PR입니다.**

**개선 코드** — `BaseConfig`가 3개 모드의 공통 부모이므로 분기만 하면 전부 적용된다:

```java
        return config -> {
            BaseConfig<?> serverConfig = serverConfigOf(config);
            if (serverConfig == null) {
                // 지원하지 않는 구성(masterSlave/replicated) — 기동을 깨뜨리지 않되, 보증이 깨졌음을 크게 남긴다.
                log.error("RedisConfig - unsupported Redisson topology, Redis timeout/retry defaults NOT applied. "
                        + "지도 조회의 요청 단위 직행 강등 보증이 성립하지 않는다(설계 결정 2). 구성을 확인할 것.");
                return;
            }
            serverConfig.setTimeout((int) commandTimeout.toMillis())
                    .setConnectTimeout((int) connectTimeout.toMillis())
                    .setRetryAttempts(retryAttempts)
                    .setRetryInterval((int) retryInterval.toMillis());
        };
    }

    /** 단일·클러스터·센티널 어느 토폴로지든 같은 타임아웃 정책을 적용한다. use*Servers()는 해당 모드일 때만 호출해야 한다. */
    private static BaseConfig<?> serverConfigOf(Config config) {
        if (config.isSingleConfig())   return config.useSingleServer();
        if (config.isClusterConfig())  return config.useClusterServers();
        if (config.isSentinelConfig()) return config.useSentinelServers();
        return null;
    }
```

`Config`가 `isSingleConfig()` / `isClusterConfig()` / `isSentinelConfig()`를 모두 public으로 제공하고,
`SingleServerConfig`·`ClusterServersConfig`·`SentinelServersConfig`가 전부 `BaseConfig`를 상속해
`setTimeout` / `setConnectTimeout` / `setRetryAttempts` / `setRetryInterval`을 갖고 있다는 것은
바이트코드로 확인했습니다.

**개선 이유**:

**안전 보증이 인프라 구성에 따라 조용히 켜졌다 꺼졌다 하면, 그건 보증이 아니라 우연입니다.**
지금 코드는 "단일 서버일 때만 참인 보증"인데 그 조건이 코드 어디에도 계약으로 적혀 있지 않습니다.
3줄로 세 토폴로지 전부를 커버하면 조건 자체가 사라집니다.

그리고 도달 불가능한 나머지 경우(masterSlave/replicated)는 `warn`이 아니라 `error`가 맞습니다.
`warn`은 "알아두면 좋은 것", `error`는 "누가 봐야 하는 것"인데, 이건 **설계가 약속한 보증이 깨진 상태**입니다.
알림 룰에 잡히는 레벨이어야 합니다.

---

### [MAJOR-3] CRITICAL 수정 자체에만 자동 회귀 가드가 없다 — 값 4개가 전부 "사람이 기억해야 하는 것"으로 남았다

**현재 상태** — `grep -rl "redisTimeoutCustomizer\|retryAttempts" src/test` 결과 **0건**.

**문제점**:

1회차 결론이 *"규율을 사람이 아니라 시스템이 지키게 하라"* 였고, FIX 3(발행 계약 테스트)과 FIX 5([R3] 2건)가
정확히 그 처방을 실행했습니다. 그런데 **정작 CRITICAL 수정에는 테스트가 하나도 없습니다.**
실측은 했지만 실측은 사람의 손이고, 사람의 손은 다음 배포에 없습니다.

조용히 되돌아갈 수 있는 경로가 최소 셋입니다.

1. **누군가 gitignore된 yml에 `spring.data.redis.timeout: 3s`를 적는다.** — 저장소에서 안 보입니다.
   리뷰도 못 합니다. `application-*.yml` 3개가 전부 `.gitignore:40-42` 대상입니다.
2. **Redisson 업그레이드.** `RedissonAutoConfigurationCustomizer` 적용 지점은 `redisson()` 메서드 내부 구현이고
   공개 계약이 아닙니다. `AutoConfiguration.imports`가 V3로 바뀌거나 커스터마이저 적용 순서가 바뀌면
   컴파일은 통과하고 값만 안 먹습니다.
3. **MAJOR-2의 토폴로지 변경.**

셋 다 **컴파일 에러도, 테스트 실패도 없이** 60초(또는 7.5초) 스톨을 되돌립니다.

**개선 코드** — 커스터마이저는 순수 람다이므로 스프링 컨텍스트 없이 5줄로 고정된다:

```java
class RedisConfigTest {

    /**
     * 이 값들이 셀 캐시의 "요청 단위 직행 강등"을 실제로 발동시키는 유일한 근거다(설계 결정 2).
     * Redisson 기본값(응답 3s · 재시도 3회 × 1.5s)이면 호출자는 ~7.5초를 readOnly 트랜잭션 안에서 기다린다.
     * 값을 바꾸려면 이 테스트를 먼저 바꿔야 한다 — 실측 없이 되돌아가는 것을 막기 위함이다.
     */
    @DisplayName("Redis 타임아웃 커스터마이저는 Redisson 기본값을 응답 500ms · 재시도 1회 × 200ms로 덮어쓴다")
    @Test
    void customizer_OverridesRedissonDefaults() {
        // given : 자동설정이 만드는 것과 같은 단일 서버 Config
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");

        // when
        new RedisConfig().redisTimeoutCustomizer(
                Duration.ofMillis(500), Duration.ofMillis(500), 1, Duration.ofMillis(200)).customize(config);

        // then : 호출자 대기 상한 = (timeout + retryInterval) × (retryAttempts + 1) ≈ 1.4s
        SingleServerConfig applied = config.useSingleServer();
        assertThat(applied.getTimeout()).isEqualTo(500);
        assertThat(applied.getConnectTimeout()).isEqualTo(500);
        assertThat(applied.getRetryAttempts()).isEqualTo(1);
        assertThat(applied.getRetryInterval()).isEqualTo(200);
    }
}
```

여기에 MAJOR-2를 함께 고친다면 클러스터 Config로도 같은 단언을 한 번 더 하면 됩니다(3줄 추가).

더 강한 가드를 원한다면 통합 테스트 한 건 — TestContainers Redis에 `DEBUG SLEEP 3`을 던지고
`courseReadModelReader.findCoursesForMap(...)`이 **3초가 아니라 ~1.5초 안에** 정상 결과를 돌려주며
`lookups{degraded}`가 증가하는지 확인하는 것입니다. 다만 이건 시간 의존이라 CI에서 흔들릴 수 있으니,
**위 단위 테스트가 비용 대비 가치가 훨씬 큽니다.**

**개선 이유**:

이 PR의 안전 보증 전체가 정수 4개에 걸려 있습니다. 그런데 그 4개는 지금
**코드 기본값 + javadoc + 사람의 실측 기억**으로만 지탱됩니다.
1회차에서 [R3] try/catch에 테스트를 붙이라고 한 근거와 정확히 같은 논리입니다 —
"지금 코드는 옳지만, 그 옳음이 사람의 기억으로만 지탱된다."

그리고 이건 **PR 자신이 세운 기준**이기도 합니다. `CourseReadModelWriter`가 `Propagation.MANDATORY`로
규율을 런타임에 강제하고, `RunningCommandServiceTest`가 `AtomicBoolean`으로 [R2]를 고정한 그 PR입니다.
같은 기준을 CRITICAL 수정 자신에게 적용하지 않을 이유가 없습니다. 5줄입니다.

---

### [MINOR-1] `spring.data.redis.retry-attempts` / `retry-interval`은 Spring Boot 프로퍼티가 아니다

`RedisConfig.java:62-63`. Spring Boot 3.5의 `RedisProperties`(`@ConfigurationProperties("spring.data.redis")`)에는
`retry-attempts`/`retry-interval`이 **없습니다**(host·port·username·password·database·url·client-name·client-type·
connect-timeout·timeout·ssl·lettuce·jedis·sentinel·cluster가 전부). `ignoreUnknownFields`가 기본 true라
바인딩 실패는 나지 않지만, 부작용이 둘 있습니다.

- IDE·`spring-configuration-metadata`가 "알 수 없는 속성"으로 표시해, 운영자가 **오타라고 판단하고 지울 수 있습니다.**
- Spring Boot가 나중에 같은 이름의 키를 도입하면(Lettuce 재시도 등) **의미가 조용히 갈라집니다.**

`timeout`/`connect-timeout` 두 개는 자동설정과 값을 공유해야 하므로 지금 위치가 맞습니다.
반면 재시도 2개는 Redisson 고유 개념이므로 `ghostrunner.redis.retry-attempts` 같은 자체 네임스페이스가
더 정직합니다. 두 그룹이 갈리는 게 오히려 "어느 쪽이 프레임워크 계약이고 어느 쪽이 우리 것인가"를 드러냅니다.

### [MINOR-2] `candidatesOf`가 degraded를 다루는 것처럼 보이지만, degraded 입력이 오면 금지된 경로로 간다

`CourseReadModelReader.java:146-154`. `fullHit()`이 `!degraded && ...`라서 이 메서드는 이제
**degraded를 아는 것처럼 읽힙니다.** 그런데 실제로 `degraded == true`인 lookup이 들어오면
`fullHit()`이 false → `fillMissedCells(lookup.missedCells())`로 내려가고, degraded의 `missedCells`는
`CellCacheLookup.degraded()`가 넣은 **커버링 전체**입니다. 즉 설계가 명시적으로 금지한
"전체-미스 강등 → 대형 채움 쿼리 + 재적재"(M15 / 결정 2)를 정확히 수행합니다.

지금은 호출부가 앞에서 걸러 도달 불가능하지만, **"괜찮아 보이는 코드"가 최악의 동작을 숨기고 있는 형태**라
`candidatesOf`를 다른 곳에서 재사용하려는 사람이 함정을 밟습니다. javadoc에
"degraded는 호출 전에 걸러져 있어야 한다 — 여기서 fill로 내려가면 M15가 재현된다" 한 줄이면 충분합니다.

### [MINOR-3] 요청당 Redis 대기 상한은 1.45초가 아니라 최대 2왕복(~2.9초)이다

`RedisConfig.java:56`의 "같은 조건 실측 1.45s"는 **커맨드 1회** 기준입니다.
지도 조회 1건이 실제로 Redis를 만지는 횟수는 최대 2회입니다.

```
lookup(MGET)  ~1.45s  →  (degraded 아니면) fillMissedCells → putAll(파이프라인 SET)  ~0.7~1.45s
                          └ 둘 다 @Transactional(readOnly = true) 안, DB 커넥션 보유 중
```

degraded면 `queryDirect`로 빠져 `putAll`에 도달하지 않으므로 최악은 "MGET은 성공했는데 그 직후 느려진" 경우입니다.
60초 → 2.9초면 20배 개선이고 수용 가능한 값이지만, 문서에 적힌 숫자와 실제 상한이 2배 다르면
용량 산정(HikariCP 풀 크기)을 할 때 어긋납니다. javadoc에 "요청당 최대 2왕복"을 한 줄 덧붙이는 것으로 충분합니다.

### [MINOR-4] `connect-timeout: 500ms` + `retryAttempts: 1`은 부팅 실패 여지를 넓힌다

Redisson은 `lazyInitialization` 기본값이 false라 `Redisson.create(config)`에서 **즉시 연결**하고,
실패하면 `RedisConnectionException`으로 **애플리케이션이 뜨지 않습니다.**
연결 타임아웃이 10,000ms → 500ms, 재시도가 3 → 1로 줄었으므로 부팅 시 허용 지연이 크게 좁아졌습니다.

VPC 내 ElastiCache라면 TCP 연결은 1ms 수준이라 실무상 문제 없을 가능성이 높습니다.
다만 **롤링 배포가 ElastiCache 페일오버와 겹치는 순간**에는 새 인스턴스가 crash-loop에 빠질 수 있습니다.
`timeout`(응답)은 짧아야 강등이 걸리지만 `connect-timeout`은 강등과 직접 관계가 없으므로,
둘을 같은 값(500ms)으로 묶을 이유가 없습니다. 연결은 2s 정도로 여유를 주는 편이 목적에 더 맞습니다.
dev 배포로 한 번 확인하고 결정하시죠.

### [MINOR-5] 발행 계약 테스트가 "실질 이빅트 발행" 2곳 중 1곳만 고정한다

`RunFinishedEvent`/`RunUpdatedEvent` 발행은 4곳인데, **지도 캐시에 실제로 의미가 있는 것은 2곳**입니다.

| 발행 지점 | 리드모델 존재 | 이빅트 의미 | 계약 테스트 |
|---|---|---|---|
| `createRunAndCourse:70` | ❌ (신규 코스는 항상 비공개 → `applyRun`이 스킵) | 헛도는 DEL | 불필요 |
| `publishCourseRunEvents:142` | ✅ | **완주 직후 TOP4 반영** | ✅ 있음 |
| `updateRunningName:174` | 변경 없음 | 헛도는 DEL | 불필요 |
| `updateRunningPublicStatus:184` | ✅ (`recalculate`로 TOP4 변동) | **러닝 공개 전환 반영** | ❌ **없음** |

`updateRunningPublicStatus_recalculatesCourse`는 `recalculate`만 검증하고 `publishEvent`는 보지 않습니다.
`:182-183` 주석이 "존치"라고 적고 있지만, 그 존치를 지키는 건 다시 사람의 눈입니다.
기존 테스트에 `verify(applicationEventPublisher).publishEvent(any(RunUpdatedEvent.class));` 한 줄이면 닫힙니다.

---

## 핵심 불변식 재확인 (수정 후)

| # | 불변식 | 1회차 | 2회차 | 근거 |
|---|--------|------|------|------|
| 1 | 넣은 곳 = 지울 곳 = 찾는 곳 | ✅ | ✅ | `GeoCell.of`가 적재(`groupByStartCell:201`)·이빅트(`Listener:58,91`)·조회(`covering`)에서 동일 인코딩. FIX 라운드에서 `GeoCell` 무변경 |
| 2 | 원 ⊆ 박스 파리티 | ✅ | ✅ | `GeoDistance:24`가 여전히 `BoundingBox.KILOMETERS_PER_LAT_DEGREE` **참조**. 값 복제 0. 양 경로 모두 `withinRadius` 종결(`Reader:103, 117`) |
| 3 | 강등 4갈래 수렴 · degraded ≠ 전셀미스 | ✅ | ✅ | 4갈래 전부 `queryDirect`. FIX 4가 분기 형태만 바꿨고 수렴점 불변. **그리고 이제 "느린 Redis"에서도 실제로 발동한다** |
| 4 | 적재 규칙(필터 앞·미스 셀만·LIMIT 시 전체 스킵) | ✅ | ✅ | `candidatesOf` 안에서 적재 완결, `cacheUnlessTruncated` 무변경 |
| 5 | 이빅트 커버리지(쓰기 7경로) | ✅ | ✅ | 신규 3 + 기존 3 유지. **주석이 사실과 일치하게 되어 삭제 사고 경로가 닫힘** |
| 6 | 외부 API 불변 | ✅ | ✅ | `CourseApi:36-52` 무변경, `CourseMapResponse` 무변경, `ErrorCode`는 C-005 제거만(주석으로 재사용 금지 명시), `RegionApi` 존치 |
| 7 | 동시성(AFTER_COMMIT · readOnly 내 I/O · 채움-이빅트 레이스) | ⚠️ | ✅ | `readOnly` 내 대기 상한 60s → ~2.9s. AFTER_COMMIT 예외 미전파가 **테스트로 고정**됨. 레이스는 결정 4로 수용 유지 |
| 8 | 실패 모드 → 5xx 방지 | ⚠️ | ✅ | 지연형 장애가 강등으로 흡수됨. 남은 5xx 경로는 셀 캐시 밖(auth·rate limit) → MAJOR-1 |
| 9 | 테스트 순서 독립성 | ⚠️ | ✅ | 셀 키를 만드는 테스트 클래스 **5개 전부** `@BeforeEach` 정리 보유 |
| 10 | 죽은 코드 | ⚠️ | ✅ | `fullHit()` 사용처 생김. `grep -rn "fullHit" src/main` → 정의 1 + 사용 1 |

---

## 설계 문서 대비 차이 (변동분만)

| 항목 | 설계 | 구현 | 비고 |
|------|------|------|------|
| `CellCacheLookup.fullHit()` | 공개 API (§3-4) | `Reader.candidatesOf:147`에서 사용 | ✅ **해소** (1회차 MINOR-1) |
| Redis 커맨드/연결 타임아웃 | **언급 없음** | `RedisConfig.redisTimeoutCustomizer` 신설 | ⚠️ §5 변경 파일 목록·§8-1·§8-2 미반영 → MAJOR-1 |
| Redis 재시도(3→1, 1500→200ms) | **언급 없음** | 동상 | ⚠️ 동상. 영향권이 course 도메인 밖 |
| §8-1 "`readOnly` 내 Redis I/O — +~1ms" | +~1ms | 정상 시 +~1ms, 장애 시 상한 ~2.9s | 수치 갱신 필요 (MINOR-3) |
| `src/test/resources/application.yml` 플래그 명시 | 변경 대상 (§5) | 미반영 | 1회차 PASS 판정 유지(재지적 아님) |
| 테스트 16건 | §6 | 신규 2건 추가로 18건 상당 | ✅ [R3] 커버리지 설계 초과 달성 |

---

## 1회차 대비 변화 요약

| 1회차 항목 | 판정 | 2회차 결과 |
|---|---|---|
| [CRITICAL-1] Redis 커맨드 타임아웃 부재 | FIX | ✅ **해소** (전제 교정 + 재시도까지 조정 + 실측 검증) |
| [MAJOR-1] 셀 값 개수 상한 없음 | DEFER | — 재지적 없음 |
| [MAJOR-2] 파리티 테스트 셀 캐시 미정리 | FIX | ✅ **해소** |
| [MAJOR-3] 발행부 낡은 주석 | FIX | ✅ **해소** (주석 4곳 + 계약 테스트 1건) |
| [MINOR-1] `fullHit()` 미사용 | FIX | ✅ **해소** |
| [MINOR-2] 롤백 플래그 yml 미선언 | PASS | — 재지적 없음 |
| [MINOR-3] `evictions{ok}` 태그 미분리 | DEFER | — 재지적 없음 |
| [MINOR-4] 가드 warn 로그 원시 좌표 | PASS | — 재지적 없음 |
| [MINOR-5] `CellBucket` 가변 리스트 | PASS | — 재지적 없음 |
| [MINOR-6] [R3] 테스트 없음 | FIX | ✅ **해소** (제안보다 강한 형태) |

**신규 지적: MAJOR 3건 + MINOR 5건. 전부 `RedisConfig` 커스터마이저 도입에서 파생됐고, CRITICAL은 0건.**

---

## 선배 개발자의 한마디

**첫째, 이번에 제일 잘한 건 "리뷰어 말을 검증한 것"입니다. 그 습관을 자기 수정에도 적용하세요.**

`LettuceClientConfigurationBuilderCustomizer`를 그대로 넣었으면 이 PR은
"CRITICAL 해결 완료"라는 커밋 메시지와 함께 머지됐을 겁니다. 그리고 6개월 뒤 Redis가 느려진 새벽에,
아무도 강등이 안 걸리는 이유를 모른 채 대시보드만 봤겠죠. **틀린 처방을 정확히 실행하는 것이
아무것도 안 하는 것보다 위험한 이유**가 이겁니다 — 고쳤다는 기록이 남아서 다시 안 봅니다.

그 습관을 한 걸음만 더 밀어주세요. 지금 이 수정의 정확성은 **한 사람이 한 번 측정한 1,453ms**에 걸려 있습니다.
그 측정은 커밋에 없습니다. `spring.data.redis.timeout: 3s` 한 줄이 gitignore된 yml에 들어가면,
Redisson이 업그레이드되면, 클러스터로 옮기면 — 조용히 되돌아갑니다.
**측정을 테스트로 바꾸는 데 5줄입니다.** MAJOR-3입니다.

**둘째, "전역 설정"은 그 이름이 곧 영향 반경입니다.**

이 커스터마이저는 `course` 도메인의 캐시 설계 때문에 태어났지만,
이름 그대로 **전역**입니다. 로그인이, 페이스메이커 처리율 제한이, 푸시 멱등이 같은 값을 씁니다.
그중 강등할 줄 아는 건 셀 캐시 하나뿐이고요.

저는 예전에 HTTP 클라이언트 타임아웃을 전역으로 3초로 줄여서 결제 콜백을 죽인 적이 있습니다.
줄인 이유는 정당했습니다 — 리포트 API가 스레드를 잡아먹고 있었거든요.
근데 그 결정을 리포트 API 코드 옆 주석에만 적었습니다. 결제팀은 몰랐고,
장애 회고에서 "왜 이 값이 3초죠?"라는 질문에 아무도 대답을 못 했습니다.

**변경의 이름과 변경의 영향 반경이 다를 때, 그 간극을 메우는 건 문서입니다.**
설계 문서 §8-1 한 줄, §8-2 한 줄. 그게 다음 사람이 원인에 도달하는 유일한 경로입니다.

셀 버킷 자체는 여전히 잘 만들었고, 이번 라운드로 **"장애 때 알아서 강등됩니다"가 드디어 참이 됐습니다.**
남은 세 개는 그 참을 지키는 장치입니다. 두 개는 3줄과 5줄이니 같이 넣고 나가시죠.

---

## 실용주의 판정 (Pragmatic Action Decision) — 2회차

> 판정자: Pragmatic Action Decider
> 판정 기준: The Pragmatic Programmer (Andrew Hunt & David Thomas) 원칙 기반
> 작업 범위: `refactor/course-map-cell-bucket-cache` — 지도 조회 캐시를 셀 버킷(geohash p6)으로 전환.
> 1회차 FIX 5건은 전부 해소 확인. **이번 신규 8건은 전부 CRITICAL-1 수정으로 도입한 `RedisConfig.redisTimeoutCustomizer`에서 파생됐다.**
> 이 PR이 내건 안전 보증: "Redis 장애 시 요청 단위 직행 강등". **전역 설정 변경이 셀 캐시 밖 소비자의 실패 의미를 바꾼 부분도 이 PR의 책임 범위로 본다** — 다만 *해결 수단*은 비용-이익으로 따로 판단했다.

### 판정 전 사실 확인 (판정에 반영된 제약)

1. **`application-*.yml`은 전부 gitignore, 베이스 yml 없음.** → yml 수정 제안은 실행 불가. 코드 `@Value` 기본값이 이 저장소의 관례다. (1회차와 동일)
2. **Redisson `Config`는 `isSingleConfig()`/`isClusterConfig()`/`isSentinelConfig()`를 모두 public으로 제공하고, `SingleServerConfig`·`ClusterServersConfig`·`SentinelServersConfig`는 전부 `BaseConfig<T extends BaseConfig<T>>`를 상속해 `setTimeout`/`setConnectTimeout`/`setRetryAttempts`/`setRetryInterval`과 대응 getter를 갖는다.** `javap`로 직접 확인했다(`redisson-3.27.2.jar`). → MAJOR-2·MAJOR-3의 개선안은 **실행 가능**하다.
3. **인프라 현황: 단일 노드 Redis, 클러스터 전환 계획 없음. 트래픽 소규모(9.5개월 조회 18,866건).** → "언젠가 클러스터로 가면"류 논거의 가중치를 낮췄다.
4. **소비자별 재시도 세분화는 사실상 불가능하다.** Redisson은 클라이언트 단위로 재시도 정책을 갖는다 — 소비자별로 나누려면 `RedissonClient` 인스턴스를 하나 더 띄워 커넥션 풀·설정·주입 경로를 이중화해야 한다. **인증 도메인을 살리자고 새 인프라 컴포넌트를 들이는 것은 이 PR의 문제 크기에 비해 과하다.** → MAJOR-1의 해결 수단은 "세분화"가 아니라 "문서화 + 관측"으로 교정했다.

### 판정 요약

| 이슈 | 심각도 | 판정 | 근거 원칙 |
|------|--------|------|-----------|
| [MAJOR-1] 전역 재시도 축소가 강등 경로 없는 3개 도메인의 실패 의미를 바꿈 — 미문서화 | MAJOR | 🔴 **FIX** (문서화 한정) | 새벽 3시 장애 콜(Tip #38) — 롤백 레버가 코드에만 있으면 장애 중에 grep해야 한다. 1회차 MINOR-2를 PASS한 근거("키가 운영 체크리스트에 있다")를 **이번 4개 키에 그대로 적용**하면 결론이 뒤집힌다 |
| [MAJOR-2] `isSingleConfig()` 가드가 조용히 fail-open | MAJOR | 🔴 **FIX** | 우연에 의한 프로그래밍(Tip #62) — 안전 보증이 인프라 구성에 따라 조용히 켜졌다 꺼진다. 클러스터 전환은 계획 없지만 **비용이 5줄이라 티켓 발행 오버헤드가 수정 비용보다 크다**(Tip #42) |
| [MAJOR-3] CRITICAL 수정 자체에 자동 회귀 가드 없음 | MAJOR | 🔴 **FIX** (범위 교정) | 이 PR이 스스로 세운 기준(`Propagation.MANDATORY`·`AtomicBoolean`·evict-throws mock)을 자기 수정에 적용. 단 **고정 대상은 "상수 4개"가 아니라 "커스터마이저가 지원 토폴로지 전부에 값을 쓴다"는 메커니즘**이다(아래 근거) |
| [MINOR-1] `spring.data.redis.retry-attempts/interval`은 Boot 프로퍼티가 아님 | MINOR | 🔴 **FIX** (MAJOR-1에 합침) | 되돌림 가능(Tip #18) — MAJOR-1에서 이 키를 **운영 문서에 못박는 순간** 이름 변경 비용이 오른다. 문서화 전에 고치면 2문자열 |
| [MINOR-2] `candidatesOf`가 degraded 입력을 받으면 금지 경로로 감 | MINOR | 🟢 **PASS** | private 메서드 + 호출부 1곳 + 바로 위 줄에서 `degraded` 반환 — 전파 위험 0 |
| [MINOR-3] 요청당 상한은 1.45s가 아니라 최대 2왕복 | MINOR | 🔴 **FIX** (MAJOR-1에 합침) | 깨진 유리창(Tip #5) — 1회차에서 "낡은 주석은 잘못된 결정"으로 4곳을 고친 그 기준. 어차피 같은 javadoc을 다시 쓴다 → 한 문장 |
| [MINOR-4] `connect-timeout: 500ms`가 부팅 실패 여지를 넓힘 | MINOR | 🟡 **DEFER** | 되돌림 가능(Tip #18) + 데이터 부족 — **양방향 트레이드**라 숫자 없이 정할 수 없다(아래 근거). 리포트 자신도 "dev 배포로 확인하고 결정"이라 적었다 |
| [MINOR-5] 발행 계약 테스트가 실질 이빅트 2곳 중 1곳만 고정 | MINOR | 🔴 **FIX** | 깨진 유리창(Tip #5) — 1회차 MAJOR-3을 FIX한 근거가 "구경로 제거 PR이 **이미 예정**돼 있다"였다. 절반만 닫으면 규율이 아니다. **1줄** |

**FIX 6건(실작업 4개 태스크) / DEFER 1건 / PASS 1건.** 총 작업량 S×3 + M×1 — 수정 파일은 `RedisConfig.java`, 설계 문서, 테스트 2개뿐이다.

---

### 수정 필수 항목 (FIX Tasks)

#### Task 1: [MAJOR-1 + MINOR-1 + MINOR-3] 전역 타임아웃 결정을 운영 문서로 끌어내고, 재시도 키를 자체 네임스페이스로 옮긴다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 4 — 새벽 3시 장애 콜 (*"You Can't Write Perfect Software. Protect your code and users from the inevitable errors." — Tip #36*).
  이 변경의 **이름은 "코스 지도 캐시"인데 영향 반경은 인증·처리율 제한·푸시 멱등**이다. 그 간극을 메우는 건 문서다.
  덧붙여 **1회차 판정과의 일관성**: MINOR-2(롤백 플래그 yml 미선언)를 PASS한 유일한 근거가 "키가 설계 §8-2에 명시돼 있다"였다. 이번에 추가된 4개 키에는 그 근거가 없다.
- **수정 대상 ①**: `src/main/java/soma/ghostrunner/global/config/RedisConfig.java:58-63` (프로퍼티 키)
  - `@Value("${spring.data.redis.retry-attempts:1}")` → `@Value("${ghostrunner.redis.retry-attempts:1}")`
  - `@Value("${spring.data.redis.retry-interval:200ms}")` → `@Value("${ghostrunner.redis.retry-interval:200ms}")`
  - **`timeout`/`connect-timeout` 두 개는 `spring.data.redis.*` 그대로 둔다.** 이 둘은 `RedissonAutoConfiguration.redisson()`이 먼저 읽어 반영하므로, 운영자가 yml로 조였을 때 자동설정과 커스터마이저가 **같은 값**을 써야 한다. 재시도 2개는 Redisson 고유 개념이라 Boot 계약이 아니다 — 두 그룹이 갈리는 것이 정직하다.
- **수정 대상 ②**: 같은 파일 javadoc(`:28-57`). 아래 3가지를 추가/교정한다.
  1. **(신규) 재시도 축소가 안전한 이유 — idempotency.** "Redisson은 응답 타임아웃 뒤 idempotent 커맨드만 재시도하고, non-idempotent 커맨드(SET·DEL·EVAL)는 재시도 없이 실패시킨다(`RedisExecutor`의 `\"retry attempts, is non-idempotent command\"`). 따라서 재시도 축소가 리프레시 토큰 저장이나 처리율 제한 Lua의 **중복 실행을 만들 수 없다.**" — 이 사실은 확인에 반나절이 드는 종류다. 확인한 사람 옆에 적어 둔다.
  2. **(신규) 무엇을 잃는가.** "`retryInterval × retryAttempts`(= 아직 전송하지 못한 커맨드의 흡수 창)가 4,500ms → 200ms로 줄었다. TCP 재전송(RTO 최소 200ms)이나 커넥션 재수립 구간이 여기 걸리면, **강등 경로가 없는 소비자**(`RefreshTokenService` → `AuthService` 5xx, `PacemakerRateLimitService.incrementRateLimitCounter` — 증가 경로에는 `@Retryable`이 없다)는 그 요청이 5xx가 된다. 변경 전에도 결과는 5xx였고 소요가 ~7.5s → ~1.4s로 줄었을 뿐이지만 **빈도는 늘 수 있다.** 배포 후 auth 5xx 비율을 함께 관측한다." (`PushIdempotencyService`는 `catch (Exception)` → `LOCK_ACQUIRED` fail-open이라 영향 없음도 한 절로 적을 것)
  3. **(교정, MINOR-3) `:56`의 "같은 조건 실측 1.45s"는 커맨드 1회 기준임을 명시한다.** "지도 조회 1건이 Redis를 만지는 횟수는 최대 2회(`lookup`의 MGET + `putAll` 파이프라인)다. 따라서 **요청 단위 대기 상한은 커맨드 1회 값의 최대 2배(~2.9s)** 로 잡아야 한다(HikariCP 풀 산정 시 이 값을 쓸 것). degraded로 빠지면 `putAll`에 도달하지 않으므로, 최악은 MGET은 성공하고 직후에 느려진 경우다."
- **수정 대상 ③**: `docs/design/course-cell-bucket-cache-design.md` 3곳
  - **§5 「변경」 목록**(`:601` 이하)에 `RedisConfig`(+`redisTimeoutCustomizer` 신설) 추가. 지금은 이 PR이 건드린 파일 목록에 전역 설정이 없다.
  - **§8-1 리스크 수용표**에 행 2개 추가:
    | 항목 | 손해 상한 | 수용 근거 |
    |---|---|---|
    | Redis 재시도 축소(3→1, 1500→200ms) | 순간 블립 시 로그인·페이스메이커 요청 5xx | 이전에도 결과는 5xx였고 소요만 ~7.5s→~1.4s. non-idempotent 커맨드는 원래 재시도되지 않으므로 **중복 실행 위험 0**. 관측: auth 5xx 비율 |
    | `readOnly` 트랜잭션 안 Redis I/O (기존 행 수치 갱신) | 정상 +~1ms, **장애 시 요청당 최대 ~2.9s(2왕복)** | 기존 "+~1ms" 표기는 정상 경로 한정이었다. 커맨드 타임아웃 도입으로 상한이 60s → ~2.9s로 확정 |
  - **§8-2 운영 체크리스트**에 8번 추가:
    ```
    8. Redis 응답/재시도 기본값은 코드(RedisConfig.redisTimeoutCustomizer)에 있다. 재배포 없이 조정 가능:
       spring.data.redis.timeout(500ms) / spring.data.redis.connect-timeout(500ms)
       ghostrunner.redis.retry-attempts(1) / ghostrunner.redis.retry-interval(200ms)
       Redis 블립 시 로그인·페이스메이커 5xx가 늘면 retry-attempts를 2~3으로 되돌린다
       (대가: 지도 조회의 강등 발동이 그만큼 늦어지고, readOnly 트랜잭션이 DB 커넥션을 더 오래 쥔다).
    ```
- **하지 말 것 (명시적 거부)**:
  - `RedissonClient`를 소비자별로 분리하거나 인증용 별도 커넥션 풀을 만드는 것 — **새 인프라 컴포넌트를 들이는 비용이 얻는 것(블립 시 로그인 5xx 빈도 감소)보다 훨씬 크다.** 이 트레이드는 리포트도 "틀리지 않았다"고 인정했다.
  - `RefreshTokenService`에 폴백/흡수를 넣는 것 — **인증 실패를 조용히 삼키는 것은 보안 결정**이지 캐시 PR이 곁다리로 할 일이 아니다.
- **예상 작업량**: S (프로퍼티 키 2 + javadoc 3문단 + 문서 3곳)
- **검증 방법**:
  1. `grep -rn "spring.data.redis.retry" src/` → **0건**.
  2. `./gradlew compileJava` 통과(=`@Value` 표현식 오타 없음). 부팅 시 `ghostrunner.redis.*`가 어디에도 없어도 기본값 1 / 200ms로 뜨는지 로컬 기동 1회 확인.
  3. 설계 문서에서 `RedisConfig`를 grep해 §5·§8-1·§8-2 **세 곳 모두** 잡히는지 확인.

---

#### Task 2: [MAJOR-2] 커스터마이저를 3개 토폴로지 전부에 적용하고, 적용 불가 시 `error`로 크게 남긴다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 7 — 우연에 의한 프로그래밍 (*"Don't Program by Coincidence. Rely only on reliable things." — Tip #62*).
  이 PR의 안전 보증이 "단일 서버 구성일 때만 참"인데 그 조건이 코드 어디에도 계약으로 적혀 있지 않다.
  **클러스터 전환 계획이 없다는 사실은 DEFER 근거가 되지 못한다** — 비용이 5줄이라 티켓을 발행하고 나중에 PR을 여는 오버헤드가 지금 고치는 비용보다 크다(*Tip #42 Take Small Steps*). 지금 열려 있는 파일에서 끝난다.
- **수정 대상**: `src/main/java/soma/ghostrunner/global/config/RedisConfig.java:64-75`
- **수정 내용**:
  ```java
  return config -> {
      BaseConfig<?> serverConfig = serverConfigOf(config);
      if (serverConfig == null) {
          // masterSlave/replicated 등 미지원 토폴로지 — 기동은 깨뜨리지 않되, 보증이 깨졌음을 크게 남긴다.
          log.error("RedisConfig - unsupported Redisson topology, Redis timeout/retry defaults NOT applied. "
                  + "지도 조회의 요청 단위 직행 강등 보증(설계 결정 2)이 성립하지 않는다. 구성을 확인할 것.");
          return;
      }
      serverConfig.setTimeout((int) commandTimeout.toMillis());
      serverConfig.setConnectTimeout((int) connectTimeout.toMillis());
      serverConfig.setRetryAttempts(retryAttempts);
      serverConfig.setRetryInterval((int) retryInterval.toMillis());
  };
  ```
  ```java
  /**
   * 단일·클러스터·센티널 어느 토폴로지든 같은 타임아웃 정책을 적용한다.
   * {@code use*Servers()}는 해당 모드일 때만 호출해야 한다 — 다른 모드에서 부르면 IllegalStateException이고,
   * 그걸 커스터마이저 안에서 던지면 애플리케이션이 아예 뜨지 않는다.
   */
  private static BaseConfig<?> serverConfigOf(Config config) {
      if (config.isSingleConfig())   return config.useSingleServer();
      if (config.isClusterConfig())  return config.useClusterServers();
      if (config.isSentinelConfig()) return config.useSentinelServers();
      return null;
  }
  ```
  - import: `org.redisson.config.BaseConfig`, `org.redisson.config.Config`.
  - **주의(컴파일)**: `BaseConfig<T extends BaseConfig<T>>`라 `BaseConfig<?>` 변수에서 setter 체이닝은 와일드카드 캡처에 걸릴 수 있다. **위처럼 문장 4개로 분리해서 쓸 것.**
  - **`useMasterSlaveServers()`/`useReplicatedServers()`는 추가하지 말 것** — `Config`에 대응하는 `isXxxConfig()` 판별자가 없어 안전하게 감지할 수 없다. 그 두 경우가 `error` 분기로 가는 것이 맞다.
  - javadoc(`:44-46` 문단)에 한 줄 추가: "지원 토폴로지는 single·cluster·sentinel이며, 그 밖의 구성에서는 이 보증이 성립하지 않고 부팅 시 `error` 로그로 드러난다."
- **예상 작업량**: S
- **검증 방법**:
  1. `./gradlew clean build -x sentryBundleSourcesJava` 그린 (722건 유지).
  2. Task 3의 cluster 케이스가 이 변경의 실증이다 — **Task 2 없이 Task 3을 먼저 돌리면 cluster 케이스가 빨개져야 한다.** 빨개지지 않으면 둘 중 하나가 잘못된 것이다.
  3. 로컬 기동 1회 — 단일 서버 구성에서 `error` 로그가 **찍히지 않는지** 확인(오탐 방지).

---

#### Task 3: [MAJOR-3] 커스터마이저의 "값을 실제로 쓴다"를 테스트로 고정한다 — 단, 고정 대상은 상수가 아니라 메커니즘

- **판정**: 🔴 FIX (리포트 제안에서 **범위 교정**)
- **근거 원칙**: 원칙 7(Tip #62) + 이 PR 자신이 세운 기준. `CourseReadModelWriter`의 `Propagation.MANDATORY`, `RunningCommandServiceTest`의 `AtomicBoolean`, FIX 5의 evict-throws mock — 규율을 기계에 맡긴 PR이 **자기 수정만 사람의 실측 기억에 맡기고 있다.**
- **범위 교정 (이게 이 태스크의 핵심이다 — 리포트 제안 그대로 하면 안 된다)**:
  리포트는 "값 4개(500/500/1/200)를 고정하라"고 했지만, **그 4개는 Task 1에서 §8-2에 운영 레버로 명시하는 값**이다. 운영자가 장애 중에 `retry-attempts`를 3으로 올리는 것은 **정상 동작**이지 회귀가 아니다. 기본값을 단언하는 테스트는 그 정당한 조정을 "테스트 실패"로 만든다 — 즉 잘못된 것을 고정하게 된다.
  또한 리포트가 든 회귀 경로 3개 중 **테스트로 잡히는 것은 토폴로지 1개뿐**이다(gitignore된 yml의 `timeout: 3s`는 문서화된 레버 그 자체라 어떤 테스트도 잡을 수 없고, Redisson 업그레이드로 커스터마이저 적용 지점이 바뀌는 것도 단위 테스트로는 못 잡는다). **잡히지 않는 것을 잡는 척하는 테스트가 제일 위험하다.**
  → 고정할 계약은 **"주어진 값을 지원 토폴로지 전부에 빠짐없이 쓴다"** 이다.
- **수정 대상**: `src/test/java/soma/ghostrunner/global/config/RedisConfigTest.java` (신규, **스프링 컨텍스트·TestContainers 없이** 순수 JUnit)
- **수정 내용**:
  ```java
  class RedisConfigTest {

      /**
       * 이 커스터마이저가 값을 실제로 쓰는 것이 셀 캐시의 "요청 단위 직행 강등"(설계 결정 2)의 유일한 근거다.
       * Redisson 기본값(응답 3s · 재시도 3회 × 1.5s)이면 호출자는 ~7.5초를 readOnly 트랜잭션 안에서 기다린다.
       * 값 자체는 운영 레버라 여기서 고정하지 않는다(설계 §8-2). 고정하는 것은 "빠짐없이 쓴다"는 계약이다.
       */
      @DisplayName("타임아웃 커스터마이저는 단일 서버 구성에 응답·연결·재시도 값을 모두 적용한다")
      @Test
      void customizer_AppliesAllValues_ToSingleServer() { ... }

      @DisplayName("타임아웃 커스터마이저는 클러스터 구성에도 같은 값을 적용한다 — 토폴로지가 바뀌어도 강등 보증은 유지된다")
      @Test
      void customizer_AppliesAllValues_ToClusterServers() { ... }
  }
  ```
  - 각 케이스 골격:
    ```java
    Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");
    // (cluster 케이스) config.useClusterServers().addNodeAddress("redis://127.0.0.1:6379");

    new RedisConfig().redisTimeoutCustomizer(
            Duration.ofMillis(500), Duration.ofMillis(500), 1, Duration.ofMillis(200))
            .customize(config);

    SingleServerConfig applied = config.useSingleServer();   // cluster 케이스는 config.useClusterServers()
    assertThat(applied.getTimeout()).isEqualTo(500);
    assertThat(applied.getConnectTimeout()).isEqualTo(500);
    assertThat(applied.getRetryAttempts()).isEqualTo(1);
    assertThat(applied.getRetryInterval()).isEqualTo(200);
    ```
  - getter 4개가 `BaseConfig`에 있고 `SingleServerConfig`/`ClusterServersConfig` 둘 다 상속한다는 것은 `javap`로 확인됨.
  - **하지 말 것**: `@SpringBootTest`/`ApplicationContextRunner`로 `@Value` 기본값까지 바인딩해 검증하려는 시도. `Duration` 변환기(`ApplicationConversionService`)와 플레이스홀더 설정을 손으로 엮어야 해서 배보다 배꼽이 크고, 얻는 것은 위에서 "고정하지 않기로 한" 상수 4개다.
- **예상 작업량**: S
- **검증 방법**:
  1. `./gradlew test --tests "*RedisConfigTest"` 그린 (실행 시간 1초 미만이어야 한다 — 컨텍스트가 뜨면 잘못 만든 것이다).
  2. **회귀 재현**: 람다를 `if (!config.isSingleConfig()) return;` 형태(현행)로 되돌리면 cluster 케이스가 빨개져야 한다. 빨개지지 않으면 Task 2가 반영되지 않은 것이다.

---

#### Task 4: [MINOR-5] 두 번째 실질 이빅트 발행 지점(`updateRunningPublicStatus`)도 계약으로 고정한다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 1 — 깨진 유리창 (*Tip #5*). 1회차 MAJOR-3을 FIX한 근거가 "구경로(`@Deprecated` 3종) 제거 PR이 **설계 결정 10으로 이미 예정**돼 있다"였다. 그 PR이 `publishEvent(running.createUpdatedEvent())`를 지우면 컴파일도 테스트도 초록인 채 **"공개 전환 후 지도 TOP4 반영"이 최대 600초 지연**된다. 실질 발행 2곳 중 1곳만 막아두면 규율이 아니라 우연이다. **1줄이다.**
- **수정 대상**: `src/test/java/soma/ghostrunner/domain/running/application/RunningCommandServiceTest.java` — 기존 테스트 `updateRunningPublicStatus_recalculatesCourse`(`:523-541`)
- **수정 내용**: 새 테스트를 만들지 말고 **기존 테스트에 발행 단언을 얹는다**(보일러플레이트 금지 원칙). `:425`의 `verify(applicationEventPublisher).publishEvent(finishedEvent);` 패턴을 그대로 따른다.
  ```java
  // given 에 추가
  RunUpdatedEvent updatedEvent = new RunUpdatedEvent(runningId, 30L, memberUuid, "러닝", true);
  when(running.createUpdatedEvent()).thenReturn(updatedEvent);

  // then 에 추가 — 이 발행이 지도 셀 이빅트를 트리거한다. 구경로 정리 시 함께 지우면 안 된다(:182-183 주석).
  verify(applicationEventPublisher).publishEvent(updatedEvent);
  ```
  - `RunUpdatedEvent`는 `soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent`(record: `runId, courseId, memberUuid, runName, isPublic`).
  - `@DisplayName`도 함께 갱신: `"updateRunningPublicStatus: 공개 여부가 바뀐 러닝의 코스를 재계산하고, 지도 셀 이빅트용 RunUpdatedEvent를 발행한다"`.
- **예상 작업량**: S
- **검증 방법**:
  1. `./gradlew test --tests "*RunningCommandServiceTest"` 그린.
  2. **실패 재현**: `updateRunningPublicStatus`의 `eventPublisher.publishEvent(...)` 한 줄을 임시 주석 처리하면 이 테스트가 빨개져야 한다. 빨개지지 않으면 목적을 달성하지 못한 것이다.

---

### 별도 티켓 권장 항목 (DEFER)

#### [MINOR-4] `connect-timeout`을 `timeout`에서 떼어내 2s 수준으로 완화

**지금이 아닌 이유** — **이건 한 방향 개선이 아니라 양방향 트레이드다.** `connect-timeout`을 올리면 부팅·페일오버 내성은 늘지만, 커넥션 재수립이 필요한 요청은 `(connectTimeout + retryInterval) × 2`만큼 `@Transactional(readOnly = true)` 안에서 더 기다린다 — 즉 이 PR이 방금 닫은 "DB 커넥션 점유" 구멍을 도로 조금 여는 방향이다. 어느 쪽이 큰지는 **숫자 없이 정할 수 없고**, 리포트 자신도 "dev 배포로 한 번 확인하고 결정하시죠"라고 적었다. 되돌림 비용은 기본값 문자열 하나라 지금 정하나 나중에 정하나 같다(*Tip #18*).

**티켓에 그대로 옮길 것**:
- **발동 조건**: 배포/롤링 중 `RedisConnectionException`으로 인한 기동 실패가 1회라도 관측되거나, ElastiCache 페일오버가 실제로 발생했을 때.
- **조치**: `spring.data.redis.connect-timeout`만 2s로 올린다(재배포 불필요 — Task 1에서 §8-2에 명시되는 레버다). 그 후 지도 조회 P99가 악화되는지 함께 본다.
- **선결 관측**: dev에서 롤링 재배포 1회 — 부팅 시 Redisson 연결 소요를 로그로 확인.

#### (추가) MAJOR-1의 "auth 5xx 관측"을 실제 대시보드/알림으로 만드는 것

Task 1은 "관측한다"를 **문서에** 적는 데까지다. 알림 룰·대시보드 패널 추가는 코드 변경이 아니고 이 PR의 머지를 막을 이유도 없다. 배포 직후 1주 관측 항목으로 별도 티켓에 둔다.

---

### 넘어가도 되는 항목 (PASS)

- **[MINOR-2] `candidatesOf`의 degraded 함정** — `private` 메서드이고 호출부가 1곳(`findCoursesForMap`)이며, **바로 그 위 줄에서** `if (lookup.degraded()) return queryDirect(...)`로 걸러진다. 리포트도 "지금은 도달 불가능"이라고 인정했다. 두 번째 호출자를 만드는 사람은 10줄짜리 메서드를 읽게 되고, 그때가 javadoc을 붙일 때다. 지금 붙이면 **"이 메서드는 degraded를 어느 정도 다룬다"는 오해**를 오히려 키울 수도 있다 — `fullHit()`이 이미 `!degraded && ...`로 타입 수준의 답을 갖고 있다. 원칙 6(직교성) 기준으로 영향 반경이 클래스 하나 안에 닫혀 있다.

---

### 실용주의 프로그래머의 한마디

**이번 8건은 전부 "CRITICAL을 제대로 고쳤기 때문에 생긴 문제"다. 그건 나쁜 신호가 아니다.**

1회차에서 리뷰어의 처방(Lettuce)이 틀렸다는 걸 실측으로 잡아낸 것 — 그게 이 PR의 최고 판단이었다. 그런데 그 판단의 부산물이 **전역 설정 변경**이었고, 전역 설정은 이름이 곧 영향 반경이다. 그래서 남은 일은 "고친 것을 지키는 장치"뿐이고, 실제로 FIX 6건 중 4건이 문서 문단과 테스트 몇 줄이다.

**FIX를 6건으로 늘린 대신, 리포트가 요구한 것 중 세 가지를 명시적으로 거부했다.**
소비자별 재시도 세분화(= `RedissonClient` 이중화), `RefreshTokenService` 폴백, `@Value` 기본값 4개를 테스트로 못박는 것. 앞의 둘은 **캐시 PR이 인프라·보안 결정을 곁다리로 하는 것**이고, 마지막은 **운영 레버를 테스트로 잠그는 것**이다. 특히 세 번째가 중요하다 — 장애 중에 `retry-attempts`를 3으로 올리는 건 정상 대응인데, 그걸 빨갛게 만드는 테스트는 지켜야 할 것을 잘못 고른 테스트다. **고정할 것은 상수가 아니라 메커니즘이다.**

**그리고 이번 판정의 절반은 1회차 판정과의 일관성에서 나왔다.**
MINOR-2(롤백 플래그)를 PASS한 근거가 "키가 §8-2에 있다"였으니, 새로 생긴 4개 키에는 같은 기준을 적용해야 한다(MAJOR-1). MAJOR-3(발행부 주석)을 FIX한 근거가 "구경로 제거 PR이 이미 예정돼 있다"였으니, 남은 절반도 닫아야 한다(MINOR-5). **판정 기준이 라운드마다 바뀌면 그건 기준이 아니다.**

완벽한 코드는 없다. 하지만 **"장애 때 알아서 강등됩니다"를 참으로 만들어 놓고, 그 참이 서 있는 4개 정수와 그 대가를 아무 데도 안 적어두는 것**은 다음 사람에게 같은 반나절을 다시 태우라는 말이다. 파일 세 개, 한 시간이면 닫힌다. 그러고 나가자.
