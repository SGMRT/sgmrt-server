# 코드 품질 리포트 — 셀 버킷(geohash p6) 캐시 전환 (iter1)

- 대상 브랜치: `refactor/course-map-cell-bucket-cache`
- 설계 문서: `docs/design/course-cell-bucket-cache-design.md`
- 리뷰 범위: 신규 8 + 변경 10 + 삭제 3 (main), 신규 5 + 변경 5 (test)
- 빌드 상태: `./gradlew clean build -x sentryBundleSourcesJava` 그린 / 719 tests, 0 failures

---

## 총평

먼저 칭찬부터 하겠습니다. 이 PR은 제가 최근 본 리팩토링 중 **"왜 이렇게 했는지"가 코드 안에 남아 있는 몇 안 되는 사례**입니다.
특히 `GeoDistance`가 `BoundingBox.KILOMETERS_PER_LAT_DEGREE`를 **참조**해서 쓰는 것(값 복제가 아니라), 그래서
"원 ⊆ 박스"가 부등식으로 성립하고 그 위에 직행/캐시 파리티가 얹히는 구조는 — 이건 취향이 아니라 **설계입니다.**
저는 "캐시 켰더니 결과가 미묘하게 다른데 재현이 안 됩니다" 라는 티켓으로 2주를 태운 적이 있는데, 그 실패 모드가
여기서는 산술적으로 닫혀 있습니다. `GeoCellTest`가 독립 참조 구현(이진 탐색 geohash)과 10,000점을 대조하는 것도
"내가 짠 코드로 내가 짠 코드를 검증"하는 자기순환을 피한 좋은 판단입니다.

강등 4갈래가 전부 `queryDirect` 하나로 수렴하는 것, 그래서 롤백이 플래그 하나라는 것도 운영자 입장에서 고맙습니다.
[R1](열거 전 O(1) 선판정) · [R2](벌크 삭제 전 좌표 수집) · [R3](AFTER_COMMIT try/catch) · [R4](MGET 크기 불일치)
네 개 다 코드에 정확히 반영돼 있고, 특히 R2는 `RunningCommandServiceTest`에서 `AtomicBoolean`으로
"영속성 컨텍스트가 비워진 뒤 초기화하면 터진다"를 **회귀 테스트로 고정**해 뒀습니다. 이건 문서에만 적어두는 것과
차원이 다릅니다.

그럼 우려 사항입니다. 제일 신경 쓰이는 건 코드가 아니라 **설정**입니다.
이 설계의 안전 보증은 "Redis가 죽으면 요청 단위로 직행 강등한다"인데, 현재 Redis 커맨드 타임아웃이 어디에도
설정되어 있지 않습니다(Lettuce 기본 60초). 제 경험상 프로덕션 Redis는 "연결 거부"로 죽는 경우보다
**"응답이 안 오는" 형태로 느려지는 경우가 훨씬 많습니다**(RDB fork 스톨, maxmemory 이빅션 폭풍, 슬로우 커맨드 블로킹).
그 상황에서 이 코드는 강등하지 않고 **`@Transactional(readOnly = true)` 안에서 DB 커넥션을 쥔 채 최대 60초를 기다립니다.**
지도 조회는 홈 화면이라 트래픽이 제일 많고, 커넥션 풀이 마르면 지도뿐 아니라 **앱 전체가 같이 넘어갑니다.**
설계가 약속한 "요청 단위 강등"이 실제로 성립하려면 타임아웃 한 줄이 필요합니다.

두 번째는 3개월 뒤 문제입니다. 셀 값에 **개수 상한이 없습니다.** 지금은 코스 수가 적어서 안 보이지만,
강남처럼 코스가 몰리는 셀 하나가 수백 개를 담기 시작하면 MGET 한 번이 메가바이트를 끌어옵니다.
최종 응답은 10개인데 말이죠.

점수는 85점입니다. 출시해도 되지만, 아래 CRITICAL 하나는 **머지 전에** 처리하시길 권합니다. 설정 두 줄입니다.

---

## 총점: 85/100 — 등급 **A** (약간의 개선 후 출시 가능)

---

## 차원별 점수

| 차원 | 점수 | 핵심 피드백 |
|------|------|-------------|
| 가독성 | 9/10 | 네이밍이 의도를 그대로 말한다. javadoc이 "무엇"이 아니라 "왜"를 적는다. `RunningCommandService`의 낡은 주석만 정리 필요 |
| 아키텍처 준수 | 9/10 | 레이어·외부 API 불변 전부 준수. `running → course.domain.events` 결합이 한 겹 늘었으나 기존 결합 수준 |
| 단일 책임 | 10/10 | 산수(GeoCell/GeoDistance) · Redis 왕복(CourseCellCache) · 오케스트레이션(Reader) · 관측(Metrics)이 깔끔히 분리 |
| 캡슐화 | 9/10 | `CellCacheLookup`이 Map을 숨기고 3가지만 노출. `CellBucket`이 가변 리스트를 그대로 받는 점만 아쉽다 |
| 테스트 품질 | 8/10 | 불변식 중심, 보일러플레이트 0. 다만 `CourseMapPathParityTest`의 셀 키 미정리는 순서 의존 플래키의 씨앗 |
| 에러 처리 | 7/10 | 흡수-신호화 패턴은 모범적. **Redis 커맨드 타임아웃 부재로 "느린 Redis" 장애가 강등되지 않는다** |
| 성능 | 8/10 | MGET 1왕복 + 파이프라인 1왕복 + 채움 쿼리 1회. 셀 값 크기 상한이 없어 장기 증폭 여지 |
| 보안 | 8/10 | 신설 4xx 없음, 키 공간 주입 불가(base32 6자 고정). warn 로그의 원시 좌표만 정리 권장 |
| 설계 일치도 | 9/10 | 12건 결정·R1~R4 전부 반영. 플래그 yml 미선언과 미사용 `fullHit()`이 차이 |
| 유지보수성 | 8/10 | 근사 상수 단일 출처·롤백 레버·설계 문서 역참조가 훌륭. 낡은 주석이 향후 삭제 사고를 부를 수 있다 |

---

## 잘한 점

### 1. 파리티를 "테스트로 확인"이 아니라 "부등식으로 보장"했다

`GeoDistance.java:24`
```java
private static final double METERS_PER_LAT_DEGREE = BoundingBox.KILOMETERS_PER_LAT_DEGREE * 1000d;
```

여기서 `111_000d`를 하드코딩했다면(설계 §3-2 의사코드는 실제로 그렇게 적혀 있습니다) 6개월 뒤 누군가
`BoundingBox`의 근사만 Haversine으로 바꾸고 `GeoDistance`는 그대로 두는 날이 옵니다. 그날 "원 ⊆ 박스"가
조용히 깨지고, 반경 경계의 얇은 고리에서 직행 경로와 캐시 경로가 다른 답을 냅니다. **재현율이 낮아서 가장 잡기 어려운 버그**죠.
참조로 묶어 두면 그 사고 자체가 성립하지 않습니다. 구현이 설계보다 나은 지점입니다.

### 2. `CellCacheLookup.degraded`를 boolean 필드로 타입에 강제한 것

`CellCacheLookup.java:21-23`. "Redis 장애"와 "전 셀 미스"를 빈 Map으로 뭉뚱그렸다면, Redis가 흔들리는 그 순간에
전 셀 미스로 오인 → `fill-limit=500` 대형 채움 쿼리 + 재적재가 모든 요청에서 동시에 터집니다.
Redis 장애가 **DB 장애로 번지는** 전형적인 2차 재해인데, 이걸 주석이나 규율이 아니라 **타입으로** 막았습니다.
`CourseCellCacheDegradeTest`가 연결 실패 / null / 크기 불일치 세 가지를 한 테스트에서 확인하는 것도 적절합니다.

### 3. [R2] 회귀를 테스트로 못 박았다

`RunningCommandServiceTest:459-473`의 `lazyCourse(...)`는 `AtomicBoolean`으로 "벌크 삭제 후에는
`createMapDataChangedEvent()`가 `LazyInitializationException`을 던진다"를 흉내 냅니다.
그래서 누가 나중에 "이벤트 발행을 `recalculate` 뒤로 옮기자"고 리팩토링하면 **테스트가 먼저 빨개집니다.**
`@Modifying(clearAutomatically = true)`의 부작용은 코드만 봐서는 절대 안 보이는 함정이고, 이건 러닝 삭제 API가
항상 500이 되는 사고였습니다. 문서로 남기는 대신 테스트로 고정한 판단이 정확합니다.

### 4. `coveringCount`를 O(1) 산술로 분리한 것 [R1]

`GeoCell.java:57-59` / `CourseReadModelReader.java:127-136`. `lat=89.99&radiusM=3000`은 인증만 있으면 누구나
보낼 수 있고(`CourseApi`의 lat/lng에 검증 애노테이션이 없습니다), 리스트 size로 판정했다면 19만 개 객체를
할당한 뒤 버립니다. 요청 몇 개면 GC가 무릎을 꿇습니다. **현행 직행 경로에는 없던 신규 증폭 벡터를 스스로 찾아
없앤 것**이라, 이런 건 리뷰에서 잡히기 전에 본인이 잡는 게 제일 값집니다.

### 5. 적재를 원 필터 **앞**에 둔 것

`CourseReadModelReader.java:146-173`. 캐시 값이 "요청자의 반경으로 자른 목록"이 되면, 200m로 조회한 사람이
적재한 값을 1km로 보는 다음 사람이 받아서 코스가 사라집니다. 침묵 오답이라 알림도 안 울립니다.
`partialFill_KeepsHitCellIntactAndFillsOnlyMissedCells` 테스트가 **적재는 200m 요청으로, 재사용은 1km 요청으로**
일부러 다르게 잡아 이 성질을 직접 겨냥한 것도 좋습니다.

---

## 개선 필요 사항

### [CRITICAL-1] Redis 커맨드 타임아웃이 없어, "느린 Redis"에서는 직행 강등이 작동하지 않고 DB 커넥션을 60초까지 물고 있는다

**현재 코드** (`src/main/resources/application-dev.yml:14`):
```yaml
  data:
    redis:
      host: localhost
      port: 6379
      # timeout / connect-timeout 미설정 → Lettuce 기본 커맨드 타임아웃 60초
```

**현재 코드** (`src/main/java/soma/ghostrunner/domain/course/dao/CourseCellCache.java:85-98`):
```java
    private List<String> fetchCellValues(List<GeoCell> covering) {
        try {
            List<String> cellValues = redisTemplate.opsForValue().multiGet(keysOf(covering));
            ...
        } catch (Exception e) {
            log.warn("CourseCellCache - MGET failed, degrade to direct query", e);
            return null;
        }
    }
```

**문제점**:

이 설계의 안전 보증(확정 결정 2 · 설계 §3-4 D2)은 **"Redis 장애 → 요청 단위 직행 강등"** 입니다.
그런데 강등 트리거는 오직 `catch (Exception)` 하나뿐입니다. 즉 **예외가 던져져야만 강등됩니다.**

제 경험상 프로덕션 Redis가 죽는 방식은 두 가지인데, 빈도는 압도적으로 후자입니다.

1. **끊어짐** — `RedisConnectionFailureException`이 즉시 나고, 이 코드는 정상적으로 강등합니다. ✅
2. **느려짐** — RDB/AOF fork 스톨, `maxmemory` 도달 후 이빅션 폭풍, `KEYS`/`SMEMBERS` 같은 슬로우 커맨드가
   싱글 스레드 이벤트 루프를 점유. 연결은 살아 있고 커맨드만 응답하지 않습니다.
   이때 Lettuce는 **기본 60초**를 기다린 뒤에야 `RedisCommandTimeoutException`을 던집니다. ❌

2번에서 무슨 일이 벌어지는지 호출 스택으로 따라가 보면:

```
CourseFacade.findCoursesByPosition   @Transactional(readOnly = true)   ← DB 커넥션 획득
  └ CourseReadModelReader.findCoursesForMap
      └ CourseCellCache.lookup → multiGet(...)                        ← 여기서 최대 60초 블로킹
```

`CourseFacade.java:157`이 `@Transactional(readOnly = true)`라 **DB 커넥션을 쥔 채로** 60초를 기다립니다.
지도 조회는 홈 화면 진입마다 호출되는 최다 트래픽 엔드포인트입니다. HikariCP 기본 풀이 10이라고 하면,
**동시 요청 10건이면 60초 동안 앱 전체의 DB 커넥션이 고갈**됩니다. 러닝 저장도, 로그인도, 알림도 같이 멈춥니다.
Redis 부분 장애가 **전면 장애로 승격**되는 겁니다.

게다가 쓰기 경로도 노출돼 있습니다. `CourseCellCacheEvictListener`는 AFTER_COMMIT에서 `evict`를 부르는데,
`redisTemplate.delete(...)`가 60초 걸리면 **커밋은 이미 끝난 러닝 완주 API가 60초 뒤에 응답**합니다.
사용자는 완주 버튼을 누르고 타임아웃을 봅니다.

설계 §8-1은 이 리스크를 "`readOnly` 트랜잭션 안에서 Redis I/O — +~1ms, DB 커넥션 보유"로 적고 있는데,
**"+~1ms"라는 전제가 성립하려면 타임아웃이 있어야 합니다.** 지금은 상한이 60초입니다.

**개선 코드** (`application-dev.yml` / `application-prod.yml` / `application-local.yml`):
```yaml
spring:
  data:
    redis:
      host: ...
      port: 6379
      # 지도 조회는 readOnly 트랜잭션 안에서 Redis를 왕복한다(설계 §8-1).
      # 커맨드가 느려지면 DB 커넥션을 쥔 채 대기하므로, "느린 Redis"도 예외로 바꿔
      # 설계가 약속한 요청 단위 직행 강등(결정 2)이 실제로 발동하게 만든다.
      timeout: 200ms          # 커맨드 타임아웃 (P99 왕복 ~1ms 대비 200배 여유)
      connect-timeout: 200ms  # 연결 수립 타임아웃
```

그리고 `CourseCellCache`의 강등 로그가 타임아웃을 구분할 수 있게 해두면 운영에서 원인 판별이 빨라집니다.

```java
        } catch (Exception e) {
            // RedisCommandTimeoutException(느려짐)과 RedisConnectionFailureException(끊어짐)은
            // 대응이 다르다 — 전자는 Redis 부하/슬로우 커맨드, 후자는 네트워크·프로세스 장애다.
            log.warn("CourseCellCache - MGET failed({}), degrade to direct query. cells={}",
                    e.getClass().getSimpleName(), covering.size(), e);
            return null;
        }
```

**개선 이유**:

타임아웃 200ms면 "느려진 Redis"가 곧바로 예외가 되고, **이미 구현되어 있는 강등 경로가 설계 의도대로 발동**합니다.
손해는 그 요청이 직행 쿼리로 도는 것뿐이고, 그건 이 PR 이전의 현행 동작과 정확히 같습니다.
DB 커넥션 점유 시간의 상한이 60초에서 0.2초로 300배 줄어들어, Redis 장애가 앱 전체로 번지는 경로가 닫힙니다.

값 근거: 셀 캐시의 정상 왕복은 MGET 1회로 수 ms 수준입니다(파이프라인 SET도 1왕복). 200ms는 정상 요청을
자르지 않을 만큼 넉넉하면서, 커넥션 풀을 지키기엔 충분히 짧습니다. `lookups{degraded}` 메트릭이 이미 있으므로
과도하게 잘리는지는 배포 직후 바로 관측됩니다.

> 참고: 이 노출 자체는 구 `@Cacheable` 경로에도 있었으므로 이 PR이 만든 결함은 아닙니다.
> 다만 **"장애 시 강등"을 명시적 안전 보증으로 내건 것은 이 PR**이고, 그 보증이 가장 흔한 장애 형태에서
> 성립하지 않으므로 여기서 닫는 것이 맞습니다.

---

### [MAJOR-1] 셀 값에 개수 상한이 없어, 코스가 몰리는 셀이 MGET 페이로드를 증폭시킨다

**현재 코드** (`CourseReadModelReader.java:165-173`):
```java
    private List<CourseMapDto> fillMissedCells(List<GeoCell> missedCells) {
        BoundingBox bounds = GeoCell.enclosingBox(missedCells);
        List<CourseMapDto> rows = readModelRepository.findCoursesForMap(
                bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng(), cellFillLimit);
        List<CellBucket> buckets = groupByStartCell(rows, missedCells);

        cacheUnlessTruncated(buckets, rows.size());
        return buckets.stream().flatMap(bucket -> bucket.courses().stream()).toList();
    }
```

**현재 코드** (`CourseCellCache.java:150-168`) — 히트한 셀의 값을 **전부** 역직렬화해 합칩니다:
```java
        for (int i = 0; i < covering.size(); i++) {
            GeoCell cell = covering.get(i);
            List<CourseMapDto> courses = deserializeOrNull(cell, cellValues.get(i));
            ...
                cachedCourses.addAll(courses);
        }
```

**문제점**:

셀 하나가 담을 수 있는 코스 수의 상한은 `cellFillLimit - 1`(=499)입니다. `CourseMapDto`는 25필드이고
그중 `routeUrl` · `thumbnailUrl` · TOP4 프로필 URL 6개가 URL 문자열이라, JSON 한 건이 보수적으로 잡아도 700~900B입니다.

- 밀집 셀 1개(코스 300개) ≈ **240KB**
- 서울 r=2000의 커버링은 35~48셀 (설계 §10 V2)
- 밀집 지역에서 절반만 그 정도로 차도 요청 1회 MGET 페이로드가 **수 MB**

그런데 **최종 응답은 10개**입니다(`CourseFacade.MAX_COURSES_PER_MAP_RESPONSE`).
수 MB를 네트워크로 끌어와 Jackson으로 역직렬화한 뒤 99.9%를 버리는 겁니다.
이건 DB 쿼리를 줄이려다 **요청 스레드의 CPU(역직렬화)와 Redis 네트워크 대역을 대신 태우는** 트레이드로 뒤집힙니다.

지금은 코스 수가 적어 안 보입니다. 확정 결정 1이 "캐시 경로는 모집단 상한 없음"이고 설계 §8-1도
`candidates` DistributionSummary를 **조기 경보**로 두었으니 의도된 수용입니다. 다만 경보에는 **행동 계획이 붙어야**
의미가 있습니다. 지금은 경보가 울렸을 때 할 수 있는 게 "fill-limit을 내린다"뿐인데, 그러면 `SKIPPED_OVER_LIMIT`가
켜져 캐시가 아예 안 채워집니다. 즉 **레버가 서로 반대 방향으로 묶여 있습니다.**

**개선 코드** — 셀 단위 상한을 두고, 넘치면 그 셀만 캐시 불가로 표시(적재 스킵)합니다:
```java
/** 한 셀이 담을 수 있는 카드 상한. 넘는 셀은 적재하지 않는다 — MGET 페이로드 폭주를 셀 단위로 끊는다. */
private static final int MAX_COURSES_PER_CELL = 100;

private void cacheUnlessTruncated(List<CellBucket> buckets, int fetchedRowCount) {
    if (fetchedRowCount >= cellFillLimit) {
        log.warn("CourseReadModelReader - cell fill limit reached ({}), skip caching for this request", cellFillLimit);
        metrics.recordFill(FillResult.SKIPPED_OVER_LIMIT);
        return;
    }

    // 과밀 셀은 값이 커서 MGET 페이로드를 부풀린다. 그 셀만 빼고 나머지는 정상 적재한다
    // (전체 스킵과 달리, 과밀은 "잘못된 값"이 아니라 "비싼 값"이라 파급을 셀 단위로 가둔다).
    Map<Boolean, List<CellBucket>> bySize = buckets.stream()
            .collect(Collectors.partitioningBy(b -> b.courses().size() > MAX_COURSES_PER_CELL));
    if (!bySize.get(true).isEmpty()) {
        log.warn("CourseReadModelReader - {} oversized cells skipped (max {} courses/cell)",
                bySize.get(true).size(), MAX_COURSES_PER_CELL);
        metrics.recordFill(FillResult.SKIPPED_OVERSIZED_CELL);
    }
    cellCache.putAll(bySize.get(false));
}
```

또는 더 근본적으로는 **캐시 값에 카드 전체가 아니라 `courseId + 좌표`만 담고**, 선별된 10개에 대해서만
카드를 조립하는 방향이 있습니다(값 크기가 1/20로 줄고, 멤버 프로필 변경 스테일 문제[결정 12]도 같이 사라집니다).
다만 이건 별도 PR 규모라 이번엔 상한 가드만으로 충분합니다.

**개선 이유**:

과밀 셀 하나가 그 셀을 커버링에 포함하는 **모든 요청**의 페이로드를 부풀립니다. 셀 단위로 끊으면 손해가
"그 셀은 매번 DB에서 채운다"로 국한되고, 이는 이 PR 이전 동작(전부 DB)과 같아 절대 나빠지지 않습니다.
전체 스킵(`SKIPPED_OVER_LIMIT`)과 달리 **나머지 셀의 캐시 이득은 그대로 유지**된다는 점이 핵심입니다.
`fill-limit`과 `MAX_COURSES_PER_CELL`이 서로 다른 축을 통제하므로 레버가 충돌하지도 않습니다.

---

### [MAJOR-2] `CourseMapPathParityTest`가 셀 캐시를 정리하지 않아, 테스트 실행 순서에 따라 깨진다

**현재 코드** (`CourseMapPathParityTest.java:49-70`):
```java
    /**
     * 구경로의 수동 캐시(course:{id}, TTL 60분)는 DatabaseCleanserExtension(테이블 truncate)이 지우지 못한다.
     * ID 재사용 시 다른 테스트의 캐시를 읽어 파리티가 오염되는 플래키를 막기 위해 매 테스트 전 정리한다.
     */
    @org.junit.jupiter.api.BeforeEach
    void clearLegacyCourseCache() {
        java.util.Set<String> keys = redisTemplate.keys("course:*");   // ← course-cells* 는 안 지운다
        ...
    }

    private static final double LAT = 37.5480;
    private static final double LNG = 127.0731;
```

**현재 코드** (`CourseCellCacheEvictListenerTest.java:64-65`) — **완전히 같은 좌표**:
```java
    private static final double LAT = 37.5480;
    private static final double LNG = 127.0731;
```

**문제점**:

이 테스트 클래스가 남긴 주석이 문제를 정확히 진단해 놓고 **신경로에는 적용하지 않았습니다.**

1. `IntegrationTestSupport`의 `REDIS_CONTAINER`는 `static`이라 **전체 테스트 JVM에서 단 하나**입니다.
   `build.gradle`에 `forkEvery` 설정도 없어 모든 통합 테스트가 같은 Redis를 공유합니다.
2. `DatabaseCleanserExtension`은 테이블만 truncate하고 Redis는 건드리지 않습니다(설계 §6 인프라 주의사항에
   명시되어 있고, 다른 세 테스트 클래스는 전부 `course-cells*`를 `@BeforeEach`에서 지웁니다).
3. `CourseMapPathParityTest`는 `courseFacade.findCoursesByPosition`을 호출합니다 = **신경로 = 셀 버킷 캐시 경유**.
4. `CourseCellCacheEvictListenerTest.runFinished_WithoutReadModel_EvictsNothing`은 이름 그대로
   **아무것도 지우지 않는 것을 검증**하므로, 끝나고 나면 `course-cells::{GeoCell.of(37.5480,127.0731)}` 키가
   값 `[]`로 **살아남습니다.** TTL은 600초라 테스트 실행 중에는 만료되지 않습니다.
5. 그 뒤 `CourseMapPathParityTest`가 같은 좌표에 코스를 넣고 조회하면, 그 셀은 **히트(빈 배열)** 로 판정돼
   코스가 응답에서 사라지고 파리티 단언이 깨집니다.

실제로 두 클래스를 함께 돌려봤습니다. 지금은 통과합니다 — JUnit의 기본 메서드 정렬이 우연히
`mapDataChanged_EvictsOnlyItsOwnCell_WithoutReadModel`(= 해당 셀을 **DEL 하는** 테스트)을 마지막에 두기 때문입니다.
즉 **테스트 메서드 이름 하나만 바꿔도 빨개집니다.** JUnit의 기본 정렬은 메서드명 해시 기반이라
"이름을 바꿨더니 관계없는 테스트가 깨졌다"는, 원인 추적에 반나절 날리는 그 종류의 플래키가 됩니다.

**개선 코드**:
```java
    /**
     * Redis는 테스트 간 공유 자원이다 — 컨테이너가 static이고 DatabaseCleanserExtension은 테이블만 지운다.
     * 구경로 캐시(course:{id})와 신경로 셀 버킷 캐시(course-cells::{cell}) **둘 다** 정리해야
     * 다른 테스트 클래스가 남긴 키를 히트로 읽는 순서 의존이 생기지 않는다.
     */
    @BeforeEach
    void clearSharedRedisCaches() {
        deleteKeys("course:*");
        deleteKeys(CacheType.Names.COURSE_CELLS + "*");
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
```

더 나아가, 네 개 테스트 클래스가 같은 정리 코드를 복붙하고 있으므로 JUnit 익스텐션으로 올리는 것이 낫습니다.
`DatabaseCleanserExtension` 옆에 `RedisCleanserExtension`을 두고 `@ExtendWith`로 붙이면,
**앞으로 셀 캐시를 타는 테스트를 새로 쓰는 사람이 이 함정을 밟지 않습니다.**

**개선 이유**:

이 클래스는 이미 "공유 Redis가 파리티를 오염시킨다"는 것을 알고 구경로용으로 방어해 두었습니다.
같은 이유가 신경로에도 그대로 적용되는데 방어만 빠졌습니다. 정리 대상 두 줄이면 순서 의존이 사라지고,
익스텐션으로 올리면 지식이 코드에 남아 후속 테스트까지 보호됩니다.
CI에서 간헐적으로 빨개지는 테스트는 **팀이 테스트를 안 믿게 만드는 가장 빠른 길**이라, 지금 닫는 게 쌉니다.

---

### [MAJOR-3] `RunFinishedEvent`/`RunUpdatedEvent` 발행부의 주석이 낡아, 향후 구경로 제거 시 지도 캐시 무효화가 조용히 사라진다

**현재 코드** (`RunningCommandService.java:68`):
```java
        eventPublisher.publishEvent(running.createFinishedEvent());   // 소비자: 코스 캐시 무효화(AFTER_COMMIT)만
```

**현재 코드** (`RunningCommandService.java:122-133`):
```java
    /**
     * 코스를 따라 뛴 러닝의 종료 이벤트를 발행한다. (남은 소비자는 전부 AFTER_COMMIT 부수효과)
     *
     * - RunFinishedEvent → 코스 캐시 무효화(CourseCacheEventListener) — 구경로 캐시 제거 시 함께 삭제 예정
     * - CourseRunEvent   → 푸시 발송(PushEventListener)
     */
    private void publishCourseRunEvents(Running running) {
```

**현재 코드** (`RunningCommandService.java:161`, `:170`):
```java
        // RunUpdatedEvent → 코스 캐시 무효화(CourseCacheEventListener)
        eventPublisher.publishEvent(running.createUpdatedEvent());
```

**문제점**:

이 PR로 `RunFinishedEvent`와 `RunUpdatedEvent`의 소비자가 **하나 더 늘었습니다** —
`CourseCellCacheEvictListener.handleRunFinished` / `handleRunUpdated`(`CourseCellCacheEvictListener.java:65-73`).
그런데 발행부 주석은 여전히 소비자가 `CourseCacheEventListener`(`@Deprecated` 구경로) **하나뿐**이라고 말합니다.
게다가 `:125`는 못을 박아 **"구경로 캐시 제거 시 함께 삭제 예정"** 이라고 적혀 있습니다.

설계 결정 10에 따라 `@Deprecated` 3종은 곧 제거될 예정입니다. 그 작업을 맡은 사람이 — 6개월 뒤의 저일 수도, 신입일 수도 있죠 —
이 주석을 그대로 믿고 `publishEvent(running.createFinishedEvent())` 한 줄을 지우면 무슨 일이 벌어질까요?

- 컴파일 통과 ✅
- 테스트 통과 — `CourseCellCacheEvictListenerTest`는 **리스너를 직접 호출**하므로(설계 §6 인프라 주의사항)
  발행부가 사라져도 초록입니다 ✅
- 프로덕션에서 **"완주하고 지도를 열면 TOP4에 내 기록이 즉시 보인다"는 §1-2 핵심 요구사항이 조용히 죽습니다** ❌

이게 딱 새벽 3시에 콜은 안 오는데 CS는 쌓이는 종류의 사고입니다. 아무도 에러를 안 보고,
"가끔 등수가 안 바뀌는 것 같아요"라는 애매한 제보만 들어옵니다. 최대 600초 스테일이라 재현도 어렵습니다.

주석은 코드가 아니지만, **삭제 판단의 근거로 읽히는 주석은 사실상 계약**입니다.

**개선 코드**:
```java
    /**
     * 코스를 따라 뛴 러닝의 종료 이벤트를 발행한다. (소비자는 전부 AFTER_COMMIT 부수효과)
     *
     * - RunFinishedEvent → (1) 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) — **존치**.
     *                          "완주 직후 지도에서 내 등수를 본다"는 요구사항이 이 발행에 걸려 있다.
     *                      (2) 구경로 코스 캐시 무효화(CourseCacheEventListener) — 구경로 제거 시 함께 삭제
     * - CourseRunEvent   → 푸시 발송(PushEventListener)
     *
     * 구경로 정리 시 (2)만 지우고 이 발행 자체는 남겨야 한다. 발행을 지우면 컴파일·테스트는 통과하지만
     * 지도 이빅트가 사라져 완주 반영이 최대 TTL(600s) 지연된다.
     */
    private void publishCourseRunEvents(Running running) {
```

그리고 더 확실한 안전판은 **주석이 아니라 테스트**입니다. `CourseCellCacheEvictListenerTest`가 리스너를 직접
호출하는 대신, 발행부까지 포함하는 경로를 한 건이라도 커버하면 삭제 사고가 테스트로 잡힙니다:

```java
// RunningCommandServiceTest — 발행 자체가 계약임을 고정한다
@Test
@DisplayName("코스 따라 러닝 완주는 지도 셀 이빅트를 트리거할 RunFinishedEvent를 발행한다")
void createRun_publishesRunFinishedEventForMapCacheEviction() {
    ...
    verify(applicationEventPublisher).publishEvent(any(RunFinishedEvent.class));
}
```

**개선 이유**:

이 PR의 이빅트 커버리지는 "신규 이벤트 3경로 + 기존 이벤트 3경로"로 완성되는데, **기존 이벤트 쪽 절반이
'삭제 예정'이라고 적힌 주석에 매달려 있습니다.** 소비자 목록을 정확히 적고 "이건 남긴다"를 명시하면
삭제 작업자가 판단할 근거가 생깁니다. 테스트까지 두면 판단 실수도 잡힙니다.
`CourseReadModelWriter`가 `Propagation.MANDATORY`로 "트랜잭션 없이 호출하면 런타임에 막는다"를
구현한 것과 같은 정신입니다 — **규율을 사람이 아니라 시스템이 지키게 하는 것.**

---

### [MINOR-1] `CellCacheLookup.fullHit()`이 아무 데서도 호출되지 않는다

`CellCacheLookup.java:36-38`. 설계 §3-4에 명시된 API지만 main·test 어디에서도 쓰이지 않습니다
(`Reader`는 `missedCells().isEmpty()`로 직접 판정합니다). 죽은 public 메서드는 "언젠가 쓰겠지" 하고 남겨두면
다음 사람이 이걸 쓰는 게 정석인 줄 알고 `degraded` 판정과 섞어 쓰는 사고가 납니다.
지우거나, `Reader.candidatesOf`가 실제로 쓰도록 바꾸거나 둘 중 하나입니다. (개인적으로는 후자를 권합니다 —
`if (lookup.fullHit()) return lookup.cachedCourses();`가 의도를 더 잘 읽히게 합니다.)

### [MINOR-2] 롤백 레버(`course.cache.cell-bucket.enabled`)가 어떤 yml에도 선언되어 있지 않다

설계 §5의 "변경" 목록은 `src/test/resources/application.yml`(플래그 명시)를 포함하는데 반영되지 않았고,
`application-dev.yml`·`application-local.yml`에도 키가 없습니다. `@Value`의 기본값(`:true`)이 있어 동작은 정상입니다.

문제는 **운영 체크리스트 4번("롤백은 `course.cache.cell-bucket.enabled=false` — 재배포 없이 직행 전환")을
실행할 사람이 그 키의 존재를 모른다**는 겁니다. 장애 중에 소스를 grep해서 프로퍼티 키를 찾는 상황은 피해야 합니다.
yml에 기본값을 명시적으로 적어두면(`enabled: true`, `fill-limit: 500`) 그 자체가 운영 문서가 됩니다.

### [MINOR-3] `evictions{ok}`가 실제로 지운 것과 지울 게 없던 것을 구분하지 못한다

`CourseCellCache.java:141-142`:
```java
            redisTemplate.delete(keyOf(cell));
            metrics.recordEviction(EvictionResult.OK);
```
`delete`는 `Boolean`(삭제 여부)을 반환하는데 버리고 있습니다. `updateRunningName`처럼 리드모델을 바꾸지 않는
경로도 `RunUpdatedEvent`를 발행하므로(설계 §4 각주 — "DEL 1회가 헛돌 뿐 무해"), `ok` 카운터에는
**헛도는 DEL이 상당수 섞입니다.** 나중에 "이빅트가 제대로 도는가"를 이 지표로 판단하려 하면 잘못된 결론에 도달합니다.
`EvictionResult.OK` / `KEY_ABSENT`로 나누면 진단 가치가 생깁니다.

### [MINOR-4] 커버링 상한 가드 warn 로그가 사용자 원시 좌표를 남긴다

`CourseReadModelReader.java:133-134`:
```java
        log.warn("... cells={}, lat={}, lng={}, radiusM={}", MAX_COVERING_CELLS, coveringCount, lat, lng, radiusM);
```
인증 사용자의 위치 좌표는 개인위치정보입니다. CLAUDE.md에 prod 러닝 데이터 마스킹 정책이 있는 만큼
같은 기준을 적용하는 게 일관됩니다. 이 로그는 극단 좌표(가드 발동)에서만 나와 빈도가 낮고 진단에 좌표가
유용한 것도 사실이니, 소수점 절삭(예: 소수 1자리 = 약 11km 해상도)이면 진단 가치는 유지하면서 식별성은 없앨 수 있습니다.

### [MINOR-5] `CellBucket`이 가변 리스트를 그대로 받는다

`CourseReadModelReader.java:206-208`에서 `groupByStartCell`이 만든 `ArrayList`가 그대로 record에 들어갑니다.
현재 흐름에서는 밖으로 새지 않아 실제 버그는 아니지만, `CellBucket`의 javadoc은 불변인 것처럼 읽힙니다
(`CellCacheLookup`은 `List.copyOf`로 실제로 불변화하고 있어 두 record의 규율이 다릅니다).
compact constructor에 `courses = List.copyOf(courses)` 한 줄이면 계약이 코드와 일치합니다.

### [MINOR-6] [R3] 계약(예외 미전파·좌표 null 가드)에 테스트가 없다

`CourseCellCacheEvictListener.java:54-62`의 try/catch와 null 좌표 가드는 "커밋 성공 후 500"을 막는
가장 중요한 방어인데, `CourseCellCacheEvictListenerTest`에는 이 경로 테스트가 없습니다.
`handleCourseMapDataChanged(new CourseMapDataChangedEvent(1L, null, null))`이 예외 없이 no-op인지
확인하는 3줄짜리 테스트면 충분합니다. 설계 §6의 "핵심 로직만" 원칙에 비춰봐도, 이건 보일러플레이트가 아니라
**불변식(AFTER_COMMIT은 절대 던지지 않는다)** 이라 커버 대상입니다.

---

## 설계 문서 대비 차이

| 항목 | 설계 | 구현 | 비고 |
|------|------|------|------|
| `GeoCell.coveringCount` 반환형 | `int` (§3-1) | `long` | ✅ 구현이 우수. 전 지구 clamp(2^30)에서 int 오버플로 여지를 없앴다 |
| `GeoDistance` 근사 상수 | `111_000d` 리터럴 (§3-2 의사코드) | `BoundingBox.KILOMETERS_PER_LAT_DEGREE` 참조 | ✅ 구현이 우수. 파리티가 값 복제에 의존하지 않는다 |
| `CellCacheLookup.fullHit()` | 공개 API (§3-4) | 정의만 되고 미사용 | ⚠️ MINOR-1 |
| `RunningCommandService` 좌표 수집 | `distinctCourseMapEventsOf` 단일 메서드 (§4 [R2]) | `distinctCoursesOf` + 스트림 2회 | 동등. `affectedCourseIds`를 같은 리스트에서 뽑아 오히려 명확 |
| `updateCourse` 발행 조건 | "이름·공개 중 하나라도 **바뀌었으면**" (§4 b~d) | "요청에 **실렸으면**"(`courseCardChangeRequested`) | 과다 이빅트 방향(안전). 변수명이 실제 의미를 정직하게 표현 |
| `evictByCourseId` null 가드 | 없음 (§3-9) | `courseId == null` 가드 추가 | ✅ 구현이 우수 |
| `src/test/resources/application.yml` 플래그 명시 | 변경 대상 (§5) | 미반영 | ⚠️ MINOR-2 |
| `CourseCellCache.lookup` 메트릭 | 셀마다 `recordCell` (§3-6) | `recordCells(hit, miss)` 일괄 | 동등. Counter 증분이라 결과 동일 |
| `covering.isEmpty()` 시 lookup 메트릭 | 명시 없음 | 아무것도 기록 안 함 | 관측 공백이지만 실사용 반경에서는 발생하지 않음 |
| 커스텀 메트릭 5종 | §3-7 표 | 이름·태그 전부 일치 | ✅ |
| 외부 API | `GET /v1/courses` 무변경, `regionId` 수용·미사용, `POST /v1/regions` 존치, 신설 4xx 없음 | 전부 일치 (`CourseApi.java:36-52`, `CourseMapResponse` 무변경, `RegionApi` 존치, `ErrorCode`는 C-005 제거만) | ✅ |
| 삭제 대상 | `CourseMapCacheEvictListener`, `CacheConfig`, `RegionNotFoundException`, `RegionRepository.findByCenterLat...`, `distinctCourseIdsOf` | 전부 삭제, 잔존 참조 0건 (`@Cacheable`/`CacheManager`/`course-map` grep 0건) | ✅ |

**중점 검증 10항목 판정**

| # | 항목 | 판정 |
|---|------|------|
| 1 | 넣은 곳=지울 곳=찾는 곳 | ✅ `GeoCell.of`가 적재(`groupByStartCell:201`)·이빅트(`Listener:58,91`)·조회(`covering`)에서 동일 인코딩. `covering`은 `encode`만 사용 |
| 2 | 원 ⊆ 박스 파리티 | ✅ 근사 상수 단일 출처 + 중심 위도 `cos` + 양 경로 모두 `withinRadius` 종결(`Reader:117, 103`) |
| 3 | 강등 4갈래 수렴 · degraded≠전셀미스 | ✅ 4갈래 전부 `queryDirect`. `degraded`가 별도 필드라 D2 성립. degraded 시 채움 쿼리 미발생 확인 |
| 4 | 적재 규칙 | ✅ 적재가 필터 앞(`candidatesOf` 안), 미스 셀만 `putAll`, fill-limit 도달 시 전체 스킵, `buckets` 평면화 반환 |
| 5 | 이빅트 커버리지 | ✅ 쓰기 7경로 전수 커버(신규 3 + 기존 3). 코스 삭제는 좌표 동봉으로 성립. AFTER_COMMIT 3핸들러 전부 try/catch — 단 MAJOR-3의 주석 리스크 |
| 6 | 외부 API 불변 | ✅ |
| 7 | 동시성 | ✅ AFTER_COMMIT 선택 근거 타당, 파이프라인으로 레이스 창 압축. `readOnly` 내 Redis I/O는 **CRITICAL-1** |
| 8 | 레이어·컨벤션·죽은 코드 | ⚠️ 레이어·Lombok·한글 javadoc·이벤트 팩토리 위치 전부 준수. 죽은 코드 1건(MINOR-1), 낡은 주석 4건(MAJOR-3) |
| 9 | 테스트 품질 | ⚠️ 불변식 중심·보일러플레이트 0. 누락: R3 예외 미전파(MINOR-6), 셀 캐시 정리 누락(MAJOR-2) |
| 10 | 실패 모드 → 5xx 방지 | ⚠️ 예외형 장애·역직렬화 실패·극단/음수/역전 좌표 전부 5xx 없음 확인. **지연형 장애는 5xx가 아니라 전면 스톨로 번짐(CRITICAL-1)** |

---

## 선배 개발자의 한마디

**첫째, "장애 시 강등한다"는 보증은 장애가 예외로 도착할 때만 참입니다.**

이 PR에서 제일 잘한 게 강등 설계인데, 역설적으로 제일 위험한 것도 강등 설계입니다.
`catch (Exception)`으로 잡는 건 **이미 도착한 실패**뿐입니다. 분산 시스템에서 제일 흔한 실패는
"실패했다"가 아니라 **"아직 대답이 없다"** 이고, 타임아웃이 없으면 그건 영원히 예외가 되지 않습니다.

제가 겪은 최악의 장애도 정확히 이 모양이었습니다. Redis가 죽은 게 아니라 느려졌는데, 커넥션 풀이 마르면서
Redis와 아무 상관없는 결제 API가 같이 죽었습니다. 대시보드에는 Redis가 "UP"으로 떠 있었고요.
원인 찾는 데 40분 걸렸습니다.

**외부 시스템을 호출하는 모든 코드에는 "얼마나 기다릴 것인가"가 명시되어 있어야 합니다.**
그게 없으면 그 코드는 라이브러리 기본값(여기선 60초)에 우리 서비스의 가용성을 위임한 겁니다.
`timeout: 200ms` 두 줄이면, 이미 잘 만들어 둔 강등 경로가 진짜로 일을 하기 시작합니다. 머지 전에 넣으세요.

**둘째, 지식은 주석이 아니라 실행되는 것에 담으세요.**

`CourseReadModelWriter`가 `Propagation.MANDATORY`로 "트랜잭션 없이 부르면 런타임에 막는다"를 구현한 것,
`RunningCommandServiceTest`가 `AtomicBoolean`으로 [R2] 회귀를 고정한 것 — 이 PR에서 제일 좋은 두 가지는
둘 다 **규율을 코드로 강제한 것**입니다.

반면 MAJOR-3(구경로 삭제 시 이벤트 발행이 같이 지워질 위험)과 MAJOR-2(테스트 간 Redis 공유)는
둘 다 **"사람이 기억해야 하는 것"** 으로 남아 있습니다. 6개월 뒤에 이 코드를 만질 사람은 이 PR의 맥락을 모릅니다.
그 사람이 실수했을 때 **테스트가 빨개지거나 컴파일이 깨지도록** 만들어 두는 것, 그게 우리가 새벽 3시에
안 깨어나는 방법입니다.

셀 버킷 자체는 잘 만들었습니다. "해싱 대상을 사람이 아니라 코스로 바꾼다"는 판단이 이 설계 전체를
단순하게 만들었고, 그 단순함이 코드에 그대로 드러납니다. 위 CRITICAL 하나만 닫고 나가시죠.

---

## 실용주의 판정 (Pragmatic Action Decision)

> 판정자: Pragmatic Action Decider
> 판정 기준: The Pragmatic Programmer (Andrew Hunt & David Thomas) 원칙 기반
> 작업 범위: `refactor/course-map-cell-bucket-cache` — 지도 조회 캐시를 regionId 결과셋 → 셀 버킷(geohash p6)으로 전환.
> 외부 API 불변(하드 제약), 서버 단독 배포, 롤백 레버는 `course.cache.cell-bucket.enabled`.
> 구경로(`@Deprecated` 3종) 제거는 범위 밖(설계 결정 10).
> 이 PR이 스스로 내건 안전 보증: **"Redis 장애 시 요청 단위 직행 강등"** — 이 보증이 성립하지 않는 결함은 범위 안으로 본다.

### 판정 전 사실 확인 (판정에 반영된 코드베이스 제약)

리포트의 개선안 중 두 건은 **이 저장소에서 그대로 실행할 수 없어** 판정 단계에서 수단을 교정했다.

1. **`application-*.yml`은 전부 gitignore 대상이다** (`.gitignore:40-42` — local/dev/prod). 베이스 `application.yml`도 없고,
   추적되는 yml은 `src/test/resources/application.yml` 하나뿐이다(`git ls-files` 확인).
   → CRITICAL-1의 "yml에 `timeout: 200ms` 두 줄"은 **커밋할 수 없다.** 서버에 배포되는 형태로 남기려면
   추적 파일인 `RedisConfig.java`에 코드 기본값으로 넣어야 한다(코드베이스의 `@Value` 기본값 관례와 동일).
   같은 이유로 MINOR-2(플래그 yml 선언)는 실행 불가능한 제안이다.
2. **분산락은 Lettuce가 아니라 Redisson 클라이언트를 쓴다** (`RedisDistributedLockManager` → `RedissonClient`).
   따라서 Lettuce 커맨드 타임아웃을 전역으로 낮춰도 **락 대기(blocking)에 영향이 없다.**
   나머지 Lettuce 사용처(RefreshToken, 처리율 제한 Lua, 구경로 코스 캐시, 셀 캐시)는 전부 단일 고속 커맨드다.
   → CRITICAL-1의 전역 적용이 안전하다는 근거이며, 이것이 없었다면 판정은 달라졌을 것이다.

### 판정 요약

| 이슈 | 심각도 | 판정 | 근거 원칙 |
|------|--------|------|-----------|
| [CRITICAL-1] Redis 커맨드 타임아웃 부재 → "느린 Redis"에서 강등 미작동 | CRITICAL | 🔴 **FIX** | 새벽 3시 장애 콜(Tip #38) + 직교성(Tip #17) — Redis 부분 장애가 무관한 전 도메인으로 번진다 |
| [MAJOR-1] 셀 값 개수 상한 없음 → MGET 페이로드 증폭 | MAJOR | 🟡 **DEFER** | Good Enough(Tip #8) + 되돌림 가능(Tip #18) — 미래 트래픽 가정, 조기 경보가 이미 있다 |
| [MAJOR-2] `CourseMapPathParityTest`가 셀 캐시 미정리 → 순서 의존 | MAJOR | 🔴 **FIX** | 우연에 의한 프로그래밍(Tip #62) + 깨진 유리창(Tip #5) — 지금 통과하는 이유가 "메서드명 해시 정렬 운" |
| [MAJOR-3] 발행부 주석이 낡아 구경로 제거 시 이빅트가 조용히 사라짐 | MAJOR | 🔴 **FIX** | 깨진 유리창(Tip #5) + 되돌림 가능(Tip #18) — 이 주석을 읽을 다음 PR이 **이미 예정**되어 있다 |
| [MINOR-1] `CellCacheLookup.fullHit()` 미사용 | MINOR | 🔴 **FIX** | Good Enough(Tip #8) — **이 PR이 만든** 죽은 public API. 만든 PR에서 닫는 게 가장 싸다 (1줄) |
| [MINOR-2] 롤백 플래그가 어떤 yml에도 없음 | MINOR | 🟢 **PASS** | 실행 불가능한 제안 — yml은 gitignore, 키는 설계 문서 운영 체크리스트 4번에 이미 있다 |
| [MINOR-3] `evictions{ok}`가 헛도는 DEL을 구분 못 함 | MINOR | 🟡 **DEFER** | 되돌림 가능(Tip #18) — 태그 값 추가는 비파괴적, 언제든 가능 |
| [MINOR-4] 가드 warn 로그의 원시 좌표 | MINOR | 🟢 **PASS** | 이 로그는 **정상 좌표에서 구조적으로 찍히지 않는다**(아래 근거) |
| [MINOR-5] `CellBucket`이 가변 리스트 수용 | MINOR | 🟢 **PASS** | 직교성(Tip #17) — 리스트가 클래스 밖으로 새지 않는다. 실버그 0 |
| [MINOR-6] [R3] AFTER_COMMIT 예외 미전파 계약에 테스트 없음 | MINOR | 🔴 **FIX** | 새벽 3시 장애 콜(Tip #38) — "커밋 성공 후 500"을 막는 불변식이 규율로만 남아 있다 (3줄) |

**FIX 5 / DEFER 2 / PASS 3.** 총 작업량은 S 4건 + M 1건이며, 전부 이 PR이 새로 들여온 코드와 이 PR이 내건 보증에 한정된다.

---

### 수정 필수 항목 (FIX Tasks)

#### Task: [CRITICAL-1] Lettuce 커맨드/연결 타임아웃을 `RedisConfig`에 코드 기본값으로 못박는다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 4 — 새벽 3시 장애 콜 (*"Crash Early. A dead program normally does a lot less damage than a crippled one." — Tip #38*).
  덧붙여 원칙 6 직교성(*Tip #17*): Redis 지연이 `@Transactional(readOnly = true)` 안에서 DB 커넥션을 붙잡아,
  Redis와 아무 관계 없는 로그인·러닝 저장까지 같이 죽는다. **무관한 것들 사이에 효과가 흐른다.**
  이 PR은 "장애 시 강등"을 명시적 안전 보증으로 내걸었고, 가장 흔한 장애 형태(느려짐)에서 그 보증이 거짓이다 → 범위 안.
- **수정 대상**: `src/main/java/soma/ghostrunner/global/config/RedisConfig.java`
  (⚠️ 리포트가 제시한 `application-dev.yml`/`application-prod.yml`은 **gitignore 대상이라 커밋 불가**. 반드시 이 파일에 넣을 것)
- **수정 내용**:
  1. `RedisConfig`에 `LettuceClientConfigurationBuilderCustomizer` 빈을 추가한다. Spring Boot의
     `LettuceConnectionConfiguration`은 커스터마이저를 **마지막에** 적용하므로, 이 빈이 Lettuce 기본값(커맨드 60초 / 연결 10초)을 이긴다.
  2. 값은 표준 프로퍼티 키에서 읽되 **기본값만 바꾼다.** 운영자가 나중에 yml/환경변수로 조일 수 있는 레버를 유지하기 위함이다.
  ```java
  /**
   * Lettuce 타임아웃 기본값을 명시한다.
   *
   * <p>지도 조회는 readOnly 트랜잭션 안에서 Redis를 왕복한다(설계 §8-1). Lettuce 기본 커맨드 타임아웃은 60초라,
   * Redis가 "끊어지는" 대신 "느려지는" 장애(fork 스톨·이빅션 폭풍·슬로우 커맨드)에서는 예외가 60초 뒤에나 도착한다.
   * 그동안 DB 커넥션을 쥔 채 대기하므로 Redis 부분 장애가 전면 장애로 번진다.
   * 타임아웃을 짧게 두어야 설계가 약속한 요청 단위 직행 강등(결정 2)이 실제로 발동한다.</p>
   *
   * <p>분산락(Redisson)은 별도 클라이언트라 이 설정의 영향을 받지 않는다.</p>
   */
  @Bean
  public LettuceClientConfigurationBuilderCustomizer redisTimeoutCustomizer(
          @Value("${spring.data.redis.timeout:200ms}") Duration commandTimeout,
          @Value("${spring.data.redis.connect-timeout:200ms}") Duration connectTimeout) {
      return builder -> builder
              .commandTimeout(commandTimeout)
              .clientOptions(ClientOptions.builder()
                      .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                      .timeoutOptions(TimeoutOptions.enabled())
                      .build());
  }
  ```
  import: `io.lettuce.core.ClientOptions`, `io.lettuce.core.SocketOptions`, `io.lettuce.core.TimeoutOptions`,
  `org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer`, `java.time.Duration`.
  3. `CourseCellCache.fetchCellValues`(`:94-97`)의 강등 로그에 예외 클래스명을 남긴다 — 타임아웃(느려짐)과
     연결 실패(끊어짐)는 운영 대응이 다르다.
  ```java
  } catch (Exception e) {
      log.warn("CourseCellCache - MGET failed({}), degrade to direct query. cells={}",
              e.getClass().getSimpleName(), covering.size(), e);
      return null;
  }
  ```
- **예상 작업량**: S (빈 1개 + 로그 1줄)
- **검증 방법**:
  1. `./gradlew test` 그린. TestContainers 통합 테스트가 200ms에 걸리면 **기본값을 500ms까지 올려도 목적(60초 → sub-second)은 유지**되므로 값만 조정한다. 200ms를 지키려고 테스트를 손대지 말 것.
  2. 로컬 Redis에 `DEBUG SLEEP 5` 후 `GET /v1/courses` 호출 → 5초가 아니라 ~200ms에 정상 200 응답,
     `ghostrunner.course.cell.cache.lookups{result=degraded}` 증가, warn 로그에 `RedisCommandTimeoutException` 기록.
  3. 배포 직후 `lookups{degraded}` 비율이 0에 수렴하는지 확인(과도 절단 여부는 이 지표로 즉시 관측된다).

---

#### Task: [MAJOR-2] `CourseMapPathParityTest`가 신경로 셀 캐시 키까지 정리하게 한다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 7 — 우연에 의한 프로그래밍 (*"Don't Program by Coincidence. Rely only on reliable things." — Tip #62*).
  이 테스트가 지금 초록인 이유는 JUnit 기본 메서드 정렬이 우연히 DEL하는 테스트를 마지막에 두기 때문이다. **메서드명 하나만 바꿔도 빨개진다.**
  덧붙여 원칙 1(*Tip #5*): 다른 세 테스트 클래스는 전부 `course-cells*`를 정리하는데 이 클래스만 빠졌다 — 규율의 예외가 하나 생기면 규율이 아니게 된다.
- **수정 대상**: `src/test/java/soma/ghostrunner/domain/course/application/CourseMapPathParityTest.java:49-59`
- **수정 내용**: `clearLegacyCourseCache()`를 아래로 교체한다. 핵심은 `"course:*"` 패턴이 `course-cells::...`를
  **매칭하지 않는다**는 것이다(7번째 문자가 `:`가 아니라 `-`). 두 패턴을 모두 지워야 한다.
  ```java
  /**
   * Redis는 테스트 간 공유 자원이다 — 컨테이너가 static이고 DatabaseCleanserExtension은 테이블만 지운다.
   * 구경로 캐시(course:{id})와 신경로 셀 버킷 캐시(course-cells::{cell})를 **둘 다** 지워야 한다.
   * 특히 CourseCellCacheEvictListenerTest는 같은 좌표(37.5480, 127.0731)를 쓰고 "아무것도 지우지 않음"을
   * 검증하는 테스트가 빈 배열 값을 남기므로, 정리하지 않으면 이 클래스가 그 셀을 히트로 읽어 코스가 사라진다.
   */
  @BeforeEach
  void clearSharedRedisCaches() {
      deleteKeys("course:*");
      deleteKeys(CacheType.Names.COURSE_CELLS + "*");
  }

  private void deleteKeys(String pattern) {
      Set<String> keys = redisTemplate.keys(pattern);
      if (keys != null && !keys.isEmpty()) {
          redisTemplate.delete(keys);
      }
  }
  ```
  - `redisTemplate`(`RedisTemplate<String,Object>`)의 keySerializer는 `StringRedisSerializer`이므로
    `StringRedisTemplate`이 쓴 셀 키도 그대로 매칭·삭제된다. 새 빈 주입은 필요 없다.
  - import 정리: `soma.ghostrunner.global.config.CacheType`, `org.junit.jupiter.api.BeforeEach`, `java.util.Set`를 추가하고
    기존의 FQCN 인라인 표기(`org.junit.jupiter.api.BeforeEach`, `java.util.Set`)를 걷어낸다.
  - `@Autowired` 필드 선언이 `@BeforeEach` 아래에 있는 현재 배치도 이 참에 필드 블록 위로 올린다.
- **예상 작업량**: S
- **검증 방법**:
  1. 두 클래스를 함께 실행: `./gradlew test --tests "*CourseCellCacheEvictListenerTest" --tests "*CourseMapPathParityTest"` 그린.
  2. **순서 의존이 사라졌음을 실증**한다 — `runFinished_WithoutReadModel_EvictsNothing`을
     `aaa_runFinished_WithoutReadModel_EvictsNothing`처럼 임시 개명해 실행 순서를 앞으로 당겨도 파리티 테스트가 초록인지 확인한 뒤 되돌린다. (수정 전에는 여기서 빨개져야 한다)
- **참고(범위 밖)**: 네 클래스가 같은 정리 코드를 복붙 중이므로 `RedisCleanserExtension` 추출이 바람직하나,
  이번 PR에서는 **플래키의 원인을 닫는 것까지만** 한다 (*Tip #42 Take Small Steps*). 추출은 아래 DEFER 목록 참조.

---

#### Task: [MAJOR-3] 이벤트 발행부 주석을 실제 소비자와 맞추고, 발행 자체를 테스트로 고정한다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 1 — 깨진 유리창 (*"Fix bad designs, wrong decisions, and poor code when you see them." — Tip #5*).
  **틀린 주석은 스타일 문제가 아니라 잘못된 결정이다.** 이 주석은 "삭제해도 된다"는 판단 근거로 읽히도록 쓰여 있고(`:125` "구경로 캐시 제거 시 함께 삭제 예정"),
  그 삭제 작업(설계 결정 10)은 **이미 다음 PR로 예정되어 있다.** 6개월 뒤 가상의 위험이 아니라 곧 도착하는 위험이다.
  원칙 2(*Tip #18*)도 걸린다 — 발행이 지워진 뒤에는 컴파일도 테스트도 통과해서 되돌릴 신호가 남지 않는다(최대 600초 스테일 + CS 제보뿐).
- **수정 대상**: `src/main/java/soma/ghostrunner/domain/running/application/RunningCommandService.java:68`, `:123-131`, `:161`, `:170`
  및 `src/test/java/soma/ghostrunner/domain/running/application/RunningCommandServiceTest.java`
- **수정 내용**:
  1. `:68` 라인 끝 주석 `// 소비자: 코스 캐시 무효화(AFTER_COMMIT)만` → 실제 소비자 2종을 적는다.
     ```java
     // 소비자(AFTER_COMMIT): 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) + 구경로 코스 캐시 무효화(CourseCacheEventListener)
     eventPublisher.publishEvent(running.createFinishedEvent());
     ```
  2. `:123-131` `publishCourseRunEvents` javadoc을 교체한다. **"이 발행은 존치"를 명시**하는 것이 이 태스크의 핵심이다.
     ```java
     /**
      * 코스를 따라 뛴 러닝의 종료 이벤트를 발행한다. (소비자는 전부 AFTER_COMMIT 부수효과)
      *
      * - RunFinishedEvent → (1) 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) — **존치**.
      *                          "완주 직후 지도에서 내 등수를 본다"(설계 §1-2)가 이 발행에 걸려 있다.
      *                      (2) 구경로 코스 캐시 무효화(CourseCacheEventListener) — 구경로 제거 시 함께 삭제
      * - CourseRunEvent   → 푸시 발송(PushEventListener)
      *
      * 구경로 정리 시 (2)의 리스너만 지우고 이 발행 자체는 남겨야 한다. 발행을 지우면 컴파일·테스트는 통과하지만
      * 지도 이빅트가 사라져 완주 반영이 최대 TTL(600s) 지연된다.
      */
     ```
  3. `:161`, `:170`의 `// RunUpdatedEvent → 코스 캐시 무효화(CourseCacheEventListener)` 두 줄도 같은 형태로
     "지도 셀 캐시 이빅트 + 구경로 무효화"로 갱신한다.
  4. `RunningCommandServiceTest`에 발행 계약을 고정하는 테스트를 **1건** 추가한다.
     `@Mock ApplicationEventPublisher applicationEventPublisher`(`:55`)가 이미 있으므로 verify 한 줄이면 된다.
     기존 "코스 따라 러닝 생성" 성공 테스트에 verify를 얹어도 되고 별도 테스트로 두어도 된다.
     ```java
     @Test
     @DisplayName("코스 따라 러닝 완주는 지도 셀 이빅트를 트리거할 RunFinishedEvent를 발행한다")
     void createRun_publishesRunFinishedEventForMapCacheEviction() {
         ...
         verify(applicationEventPublisher).publishEvent(any(RunFinishedEvent.class));
     }
     ```
- **예상 작업량**: S (주석 4곳) + S (테스트 1건) = **M**
- **검증 방법**:
  1. `./gradlew test --tests "*RunningCommandServiceTest"` 그린.
  2. **실패 재현으로 계약을 확인**한다 — `publishEvent(running.createFinishedEvent())`를 임시로 주석 처리하면
     새 테스트가 빨개져야 한다. 빨개지지 않으면 이 태스크는 목적을 달성하지 못한 것이다.
- **비고**: 주석은 사람에게, 테스트는 기계에게 남기는 것이다. 4번이 이 태스크의 **실질적 안전판**이고 1~3번은 그 이유를 설명하는 문서다. 둘 중 하나만 한다면 4번을 한다.

---

#### Task: [MINOR-1] `CellCacheLookup.fullHit()`을 `CourseReadModelReader`가 실제로 쓰게 한다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 3 — Good Enough (*"Make Quality a Requirements Issue." — Tip #8*)의 "내가 만든 코드는 내가 책임진다".
  이건 기존 코드의 묵은 죽은 코드가 아니라 **이 PR이 이번에 만든 미사용 public API**다. 만든 PR에서 닫는 비용이 1줄이고, 다음 PR로 넘기면 영원히 남는다.
  `fullHit()`은 `!degraded && missedCells.isEmpty()`라 현재의 `missedCells().isEmpty()` 단독 판정보다 **더 안전한 표현**이므로, 지우는 쪽보다 쓰는 쪽이 낫다(설계 §3-4의 공개 API 목록도 그대로 참이 된다).
- **수정 대상**: `src/main/java/soma/ghostrunner/domain/course/application/CourseReadModelReader.java:146-154`
- **수정 내용**: `candidatesOf`의 첫 분기를 `fullHit()`으로 바꾼다. `degraded`는 호출 전에 이미 걸러졌으므로 동작은 동일하다.
  ```java
  private List<CourseMapDto> candidatesOf(CellCacheLookup lookup) {
      if (lookup.fullHit()) {          // 전 셀 히트 — DB를 아예 건드리지 않는 경로
          return lookup.cachedCourses();
      }
      List<CourseMapDto> candidates = new ArrayList<>(lookup.cachedCourses());
      candidates.addAll(fillMissedCells(lookup.missedCells()));
      return candidates;
  }
  ```
- **예상 작업량**: S
- **검증 방법**: `./gradlew test --tests "*CourseReadModelReaderTest"` 그린 (특히 full-hit / partial-fill 케이스).
  이후 `grep -rn "fullHit" src/main` 결과에 `CellCacheLookup` 정의 + `CourseReadModelReader` 사용처가 **둘 다** 잡히면 된다.

---

#### Task: [MINOR-6] [R3] "AFTER_COMMIT은 절대 던지지 않는다" 불변식에 테스트를 붙인다

- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 4 — 새벽 3시 장애 콜 (*"You Can't Write Perfect Software. Protect your code and users from the inevitable errors." — Tip #36*).
  `AbstractPlatformTransactionManager.triggerAfterCommit`은 AFTER_COMMIT 예외를 **호출자에게 전파**한다 —
  커밋은 성공했는데 사용자는 500을 본다. 지금 코드는 옳지만, 그 옳음이 **try/catch 한 겹과 사람의 기억**으로만 지탱된다.
  누군가 "evict가 이미 예외를 흡수하니 이 catch는 죽은 코드"라고 판단해 걷어내면 아무 신호도 남지 않는다.
  3줄로 기계가 지키게 만들 수 있는 불변식은 기계에게 준다. (테스트 원칙 "핵심 로직만"에도 부합 — 이건 보일러플레이트가 아니라 불변식이다.)
- **수정 대상**: `src/test/java/soma/ghostrunner/domain/course/application/CourseCellCacheEvictListenerTest.java`
- **수정 내용**: 좌표 null 가드가 예외 없이 no-op임을 고정하는 테스트를 추가한다.
  ```java
  @DisplayName("좌표가 없는 지도 데이터 변경 이벤트는 예외를 던지지 않고 아무 셀도 지우지 않는다 (AFTER_COMMIT은 절대 던지지 않는다 [R3])")
  @Test
  void mapDataChanged_WithNullCoordinate_DoesNotThrow() {
      cache(GeoCell.of(LAT, LNG));

      assertThatCode(() -> listener.handleCourseMapDataChanged(
              new CourseMapDataChangedEvent(ABSENT_COURSE_ID, null, null)))
              .doesNotThrowAnyException();

      assertThat(stringRedisTemplate.keys(CELL_KEY_PATTERN)).hasSize(1);
  }
  ```
  기존 헬퍼(`cache(...)`, `CELL_KEY_PATTERN`, `ABSENT_COURSE_ID`)를 그대로 재사용한다. import: `assertThatCode`.
- **예상 작업량**: S
- **검증 방법**: `./gradlew test --tests "*CourseCellCacheEvictListenerTest"` 그린.
  `handleCourseMapDataChanged`의 null 가드를 임시로 제거하면 `GeoCell.of(null, null)`에서 NPE가 나며 빨개져야 한다.

---

### 별도 티켓 권장 항목 (DEFER)

#### [MAJOR-1] 셀 값 개수 상한 (`MAX_COURSES_PER_CELL`)

**지금이 아닌 이유** — 이건 *지금 틀린 코드*가 아니라 *미래에 비싸질 코드*다. 원칙 3(Good Enough)과 원칙 2(되돌림 가능)가 둘 다 DEFER를 가리킨다.

- 현재 트래픽은 9.5개월 조회 18,866건이고, 한 셀에 코스 300개가 몰리는 상황은 **관측된 적 없는 미래 가정**이다.
  숫자 없이 지금 `MAX_COURSES_PER_CELL = 100`을 박으면 그건 데이터가 아니라 직감으로 정한 상수다.
- 되돌림 비용이 늘지 않는다. 캐시 값은 TTL 600초로 자연 소멸하므로 마이그레이션이 없고, 스키마도 외부 계약도 아니다.
  **필요해진 날 넣는 비용 = 지금 넣는 비용**이다. 이 조건에서 미리 넣는 것은 실용주의가 아니라 추측이다.
- 조기 경보가 이미 코드에 있다 — `ghostrunner.course.cell.cache.candidates`(DistributionSummary, 설계 §8-1).

**단, 리포트의 진짜 지적("경보에 행동 계획이 없고, `fill-limit`을 내리면 `SKIPPED_OVER_LIMIT`로 캐시가 아예 안 채워져 레버가 반대로 묶인다")은 유효하다.** 티켓에 아래를 그대로 옮겨 적을 것:

- **발동 조건**: `candidates` P99 > 300 이 24시간 지속, 또는 지도 조회 P99 지연이 캐시 도입 전 대비 악화.
- **조치**: 셀 단위 상한 가드 도입 + `FillResult.SKIPPED_OVERSIZED_CELL` 태그 추가 (리포트 개선 코드 그대로).
  손해는 "그 셀만 매번 DB에서 채운다"로 국한되며 이는 이 PR 이전 동작과 같아 절대 나빠지지 않는다.
- **더 근본적 대안(별도 PR 규모)**: 캐시 값에 카드 전체가 아니라 `courseId + 좌표`만 담고 선별된 10개만 조립.
  값 크기 1/20 + 멤버 프로필 스테일(결정 12)도 함께 해소된다. 상한 가드보다 이쪽이 나을 수 있으니 티켓에서 함께 검토.

#### [MINOR-3] `evictions{ok}` / `KEY_ABSENT` 분리

**지금이 아닌 이유** — Counter에 **태그 값을 추가하는 것은 비파괴적 변경**이다(기존 대시보드·쿼리가 깨지지 않는다). 원칙 2 그대로, 나중에 고치는 비용이 지금과 같다. 게다가 이 지표로 판단을 내리려면 먼저 배포해서 데이터가 쌓여야 하는데, 아직 한 건도 없다. "헛도는 DEL이 섞인다"는 것을 **아는 상태로** 배포하고, 실제 분포를 본 뒤 필요하면 나눈다. 위 MAJOR-1 티켓과 묶어 "셀 캐시 관측 2차" 한 장으로 처리하면 된다.

#### (추가) `RedisCleanserExtension` 추출

MAJOR-2 FIX는 플래키의 원인만 닫는다. 네 테스트 클래스가 같은 정리 코드를 복붙 중인 DRY 위반(*Tip #15*)은 남는데, 이건 **보일러플레이트 중복이지 비즈니스 로직 중복이 아니라** 불일치가 오답을 만들지 않는다(최악이 "한 곳이 안 지워짐"이고 그건 MAJOR-2로 이미 닫힌다). 테스트 인프라 변경은 719개 테스트 전체에 영향을 주므로 캐시 전환 PR과 섞지 않는다 (*Tip #42*).

---

### 넘어가도 되는 항목 (PASS)

- **[MINOR-2] 롤백 플래그 yml 미선언** — `application-*.yml`은 전부 gitignore(`.gitignore:40-42`)고 베이스 `application.yml`도 없어 **애초에 커밋할 수 없는 제안**이다. 이 저장소의 관례는 `@Value` 기본값을 코드에 두는 것이고 현재 코드가 정확히 그렇다(`CourseReadModelReader:65,74`). 운영자가 키를 못 찾는다는 우려는 설계 문서 운영 체크리스트 4번에 키가 명시되어 있어 이미 해소돼 있다. 굳이 손댄다면 yml이 아니라 배포 런북 링크를 다는 것이고, 그건 코드 이슈가 아니다.
- **[MINOR-4] 가드 warn 로그의 원시 좌표** — 이 로그는 커버링 셀이 128을 넘을 때만 찍힌다. 서비스 지역(위도 33~38°) 에서 최대 반경 3km를 쓰면 커버링은 70~88셀이라 **정상 요청에서는 구조적으로 도달할 수 없다.** 즉 이 로그에 남는 좌표는 실사용자의 위치가 아니라 고위도·비정상 요청의 좌표이고, 진단 가치는 바로 그 좌표 자체에 있다. `docs/core/04-infrastructure.md:67`의 prod 마스킹 정책도 대상이 러닝 민감 필드(페이스·bpm·텔레메트리)이지 이 로그가 아니다. 절삭하면 "어떤 좌표가 가드를 때렸나"라는 유일한 단서를 잃는다.
- **[MINOR-5] `CellBucket`의 가변 리스트 수용** — `groupByStartCell`이 만든 `ArrayList`는 `CourseReadModelReader` 밖으로 나가지 않는다(직렬화 후 버려진다). 원칙 6(직교성) 기준으로 영향 반경이 메서드 하나 안에 닫혀 있어 실제 버그가 성립할 경로가 없다. `List.copyOf`는 요청당 셀 수(최대 128)만큼의 복사를 새로 만드는데, 얻는 것은 "javadoc과의 어감 일치"뿐이다. 이건 값을 치르고 사는 개선이 아니다. 다만 `CellBucket`이 언젠가 클래스 밖으로 나가는 날이 오면 그때가 `List.copyOf`를 넣을 때다.

---

### 실용주의 프로그래머의 한마디

**이 PR의 FIX 5건 중 4건은 "코드가 틀렸다"가 아니라 "옳음이 사람의 기억에 매달려 있다"이다.**

셀 버킷 설계 자체는 튼튼하다. 원 ⊆ 박스를 부등식으로 닫고, degraded를 타입으로 강제하고, 강등 4갈래를 한 곳으로 모은 것 —
이건 규율을 시스템에 새긴 좋은 사례다. 그래서 남은 결함도 전부 같은 얼굴을 하고 있다.
타임아웃(예외가 도착해야만 작동하는 강등), 낡은 주석(사람이 읽고 판단해야 하는 계약), 테스트 정리 누락(메서드명 정렬 운에 기댄 초록),
없는 불변식 테스트(try/catch를 지우면 안 된다는 암묵적 약속).

**리포트의 CRITICAL-1은 판정에서 수단만 바꿔 살렸다.** 제안된 yml은 이 저장소에서 gitignore라 커밋되지 않는다 —
즉 그대로 따랐다면 "고쳤다고 믿지만 배포 산출물에는 없는" 최악의 상태가 됐을 것이다.
*Don't Program by Coincidence*는 코드에만 적용되는 게 아니다. **고쳤다는 사실 자체도 검증 가능해야 한다.**

MAJOR-1(셀 값 상한)을 지금 하지 않는 이유는 게을러서가 아니라, **관측되지 않은 미래에 상수를 박는 것은 설계가 아니라 점(占)이기 때문**이다.
이 팀은 캐시 전략을 9.5개월 실트래픽으로 리플레이해서 골랐다. 그 기준을 여기에도 적용하자 — `candidates` P99가 말해줄 때 넣으면 된다.
되돌림 비용이 늘지 않는 결정은 미룰 수 있다는 것, 그게 *There Are No Final Decisions*의 실전적 의미다.

완벽한 코드는 없다. 하지만 **"장애 때 알아서 강등됩니다"라고 적어놓고 가장 흔한 장애에서 안 되는 것**은 깨진 유리창이다. 그 하나만 닫고 나가자.
