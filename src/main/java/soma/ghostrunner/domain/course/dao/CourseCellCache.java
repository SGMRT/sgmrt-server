package soma.ghostrunner.domain.course.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics.EvictionResult;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics.FillResult;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics.LookupResult;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.dto.query.CellBucket;
import soma.ghostrunner.domain.course.dto.query.CellCacheLookup;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.global.config.CacheType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 셀 버킷 캐시의 Redis 어댑터. (설계: docs/design/course-cell-bucket-cache-design.md §3-6)
 *
 * <p><b>책임</b> — 셀 ↔ Redis 왕복. 키 조립, MGET / 파이프라인 SET / DEL, JSON 직렬화, 실패 흡수와 신호화.
 * <b>하지 않는 것</b> — 어떤 셀을 읽고 채울지 결정(호출자 몫).</p>
 *
 * <p>값 타입이 {@link StringRedisTemplate}인 이유 — {@code RedisTemplate<String,Object>}(GenericJackson2)는
 * 값에 {@code @class} 타입 정보를 심어 패키지 이동만으로 기존 캐시가 전부 깨지고, 파이프라인에서 바이트를 직접 제어할 수 없다.</p>
 *
 * <p><b>실패는 전부 흡수한다</b> — 세 메서드 모두 "warn 로그 + 결과 태그 메트릭 + 정상 반환"으로 끝난다.
 * 밖으로 나가는 신호는 반환값뿐이다.</p>
 * <ul>
 *     <li>{@link #lookup} — 강등({@code degraded})으로 바꿔 호출자가 이 요청만 직행 쿼리로 돌게 한다.</li>
 *     <li>{@link #putAll} — best-effort. 손해는 캐시가 차갑게 남는 것뿐이라 조회 응답을 깨뜨릴 이유가 없다.</li>
 *     <li>{@link #evict} — best-effort. 손해는 최대 TTL만큼의 스테일이고, AFTER_COMMIT 리스너에서 던지면
 *         커밋이 이미 성공한 요청에 500이 나간다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCellCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CourseCellCacheMetrics metrics;

    private static final String KEY_PREFIX = CacheType.Names.COURSE_CELLS + "::";
    private static final Duration TTL = CacheType.COURSE_CELLS.getTtl();

    private static final TypeReference<List<CourseMapDto>> VALUE_TYPE = new TypeReference<>() {};

    /**
     * 커버링 셀을 MGET 1왕복으로 읽는다.
     *
     * <p>값이 깨진 셀은 <b>그 셀만</b> 미스로 강등한다 — 전체 미스로 번지면 깨진 값 하나가 요청 전체를
     * 대형 채움 쿼리로 몰아넣는다. 반대로 응답 자체를 쓸 수 없는 경우(예외·null·[R4] 크기 불일치)는
     * 인덱스로 셀과 값을 맞출 수 없으므로 미스가 아니라 강등이다.</p>
     */
    public CellCacheLookup lookup(List<GeoCell> covering) {
        if (covering.isEmpty()) {
            return CellCacheLookup.empty();
        }

        List<String> cellValues = fetchCellValues(covering);   // 키 조립 + MGET 1왕복
        if (cellValues == null) {
            return degrade(covering);                          // 응답을 쓸 수 없다 → 요청 단위 직행 강등
        }
        return classify(covering, cellValues);                 // 셀별 역직렬화 → 히트/미스 판정
    }

    /**
     * 커버링 셀의 원본 JSON을 커버링과 <b>같은 순서</b>로 읽어 온다. 쓸 수 없는 응답이면 {@code null}이다.
     *
     * <p>연결 실패와 [R4] 크기 불일치를 한 곳에서 흡수해 "쓸 수 있는 값 또는 null"로 좁힌다 — 강등 판정 분기가
     * 호출부에 하나만 남는다. 크기가 어긋난 응답을 부분 신뢰하지 않는 이유는, 몇 번째 값이 어느 셀의 것인지
     * 알 수 없어 히트를 엉뚱한 셀에 귀속시킬 수 있기 때문이다.</p>
     *
     * <p><b>커맨드 타임아웃이 이 강등의 전제다</b> — Redis가 끊어지지 않고 느려지기만 하면 예외 자체가 오지 않는다.
     * 그때는 아래 catch가 발동하지 않고 readOnly 트랜잭션이 DB 커넥션을 쥔 채 매달린다.
     * 타임아웃 기본값은 {@code RedisConfig#redisTimeoutCustomizer}가 못박는다.</p>
     */
    private List<String> fetchCellValues(List<GeoCell> covering) {
        try {
            List<String> cellValues = redisTemplate.opsForValue().multiGet(keysOf(covering));
            if (cellValues == null || cellValues.size() != covering.size()) {
                log.warn("CourseCellCache - MGET returned unusable response, degrade to direct query. requested={}, returned={}",
                        covering.size(), cellValues == null ? "null" : cellValues.size());
                return null;
            }
            return cellValues;
        } catch (Exception e) {
            // 예외 클래스명을 남긴다 — 타임아웃(느려짐)과 연결 실패(끊어짐)는 운영 대응이 다르다.
            log.warn("CourseCellCache - MGET failed({}), degrade to direct query. cells={}",
                    e.getClass().getSimpleName(), covering.size(), e);
            return null;
        }
    }

    /** 캐시를 신뢰할 수 없다는 신호. 손해는 이 요청 하나가 직행 쿼리로 도는 것뿐이다(전체-미스 강등 금지). */
    private CellCacheLookup degrade(List<GeoCell> covering) {
        metrics.recordLookup(LookupResult.DEGRADED);
        return CellCacheLookup.degraded(covering);
    }

    /**
     * 셀 버킷들을 파이프라인 1왕복으로 적재한다. (best-effort)
     *
     * <p>셀이 수십 개인 콜드 요청에서 개별 SET은 그만큼의 왕복이다. 파이프라인은 1왕복이라 지연뿐 아니라
     * 채움-이빅트 레이스의 창도 N배에서 1배로 줄어든다 — 마커 없이 레이스를 수용할 수 있는 근거다.</p>
     */
    public void putAll(List<CellBucket> buckets) {
        if (buckets.isEmpty()) {
            return;
        }

        try {
            List<CellPayload> payloads = serialize(buckets);   // 직렬화 실패 시 Redis를 아예 건드리지 않는다
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (CellPayload payload : payloads) {
                    connection.stringCommands()
                            .set(payload.key(), payload.value(), Expiration.from(TTL), SetOption.upsert());
                }
                return null;
            });
            metrics.recordFill(FillResult.STORED);
        } catch (Exception e) {
            log.warn("CourseCellCache - putAll failed (cache remains cold)", e);
            metrics.recordFill(FillResult.FAILED);
        }
    }

    /**
     * 셀 하나를 지운다. (best-effort)
     *
     * <p>DEL이라 키가 없으면 no-op이다. 실패해도 손해는 최대 TTL만큼의 스테일이며, AFTER_COMMIT 리스너에서
     * 예외를 던지면 커밋이 성공한 요청에 500이 나가므로 밖으로 내보내지 않는다.</p>
     */
    public void evict(GeoCell cell) {
        try {
            redisTemplate.delete(keyOf(cell));
            metrics.recordEviction(EvictionResult.OK);
        } catch (Exception e) {
            log.warn("CourseCellCache - evict failed (stale up to TTL). cell={}", cell.id(), e);
            metrics.recordEviction(EvictionResult.FAILED);
        }
    }

    /** MGET 응답을 셀 단위로 히트/미스 판정한다. 인덱스가 곧 셀이므로 순서 계약이 그대로 값 계약이다. */
    private CellCacheLookup classify(List<GeoCell> covering, List<String> cellValues) {
        List<GeoCell> missedCells = new ArrayList<>();
        List<CourseMapDto> cachedCourses = new ArrayList<>();

        for (int i = 0; i < covering.size(); i++) {
            GeoCell cell = covering.get(i);
            List<CourseMapDto> courses = deserializeOrNull(cell, cellValues.get(i));
            if (courses == null) {
                missedCells.add(cell);
            } else {
                cachedCourses.addAll(courses);
            }
        }

        int hitCellCount = covering.size() - missedCells.size();
        metrics.recordCells(hitCellCount, missedCells.size());
        metrics.recordLookup(lookupResultOf(covering.size(), missedCells.size()));
        return new CellCacheLookup(List.copyOf(missedCells), List.copyOf(cachedCourses), false);
    }

    /** 미스면 null. 값이 없는 것과 값이 깨진 것은 호출자 입장에서 같은 처리(그 셀만 채움)라 구분하지 않는다. */
    private List<CourseMapDto> deserializeOrNull(GeoCell cell, String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, VALUE_TYPE);
        } catch (Exception e) {
            log.warn("CourseCellCache - broken cache value, treat as single cell miss. cell={}", cell.id(), e);
            return null;
        }
    }

    private static LookupResult lookupResultOf(int coveringCellCount, int missedCellCount) {
        if (missedCellCount == 0) {
            return LookupResult.FULL_HIT;
        }
        return missedCellCount == coveringCellCount ? LookupResult.ALL_MISS : LookupResult.PARTIAL_HIT;
    }

    /** Redis 접근 이전에 직렬화를 모두 끝낸다 — 중간에 실패해 일부만 적재되는 상태를 만들지 않기 위함이다. */
    private List<CellPayload> serialize(List<CellBucket> buckets) throws JsonProcessingException {
        List<CellPayload> payloads = new ArrayList<>(buckets.size());
        for (CellBucket bucket : buckets) {
            payloads.add(new CellPayload(
                    bytes(keyOf(bucket.cell())),
                    bytes(objectMapper.writeValueAsString(bucket.courses()))));
        }
        return payloads;
    }

    private List<String> keysOf(List<GeoCell> cells) {
        return cells.stream().map(CourseCellCache::keyOf).toList();
    }

    private static String keyOf(GeoCell cell) {
        return KEY_PREFIX + cell.id();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** 파이프라인에 그대로 실을 바이트 쌍. */
    private record CellPayload(byte[] key, byte[] value) {
    }
}
