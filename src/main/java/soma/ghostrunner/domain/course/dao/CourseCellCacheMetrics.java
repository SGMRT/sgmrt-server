package soma.ghostrunner.domain.course.dao;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 셀 버킷 캐시의 관측 지점. (설계: docs/design/course-cell-bucket-cache-design.md §3-7, D7)
 *
 * <p>코드베이스의 첫 커스텀 메트릭이므로 미터명·태그 규약을 이 클래스 하나에 응집한다. 각 사용처에서
 * {@link MeterRegistry}를 직접 만지면 이름이 표류하고, 결과값을 미터명에 섞어 넣는 순간 카디널리티가 통제를 벗어난다.</p>
 *
 * <p>규약 — 이름은 {@code ghostrunner.<도메인>.<기능>.<복수형>}, 결과는 미터명이 아니라 태그 {@code result}로 표현한다.</p>
 *
 * <ul>
 *     <li>{@code cells{hit} / cells{sum}} = DB 회피율. 배포 후 리플레이 예측(22%)과 대조한다.</li>
 *     <li>{@code lookups{degraded}} 급증은 Redis 장애 신호다 — 서비스는 직행으로 동작하지만 DB 부하가 오른다.</li>
 *     <li>{@code fills{skipped_over_limit}}가 관측되면 채움 LIMIT을 상향해야 한다.</li>
 *     <li>{@code candidates}는 캐시 경로의 모집단에 상한이 없다는 결정에 대한 조기 경보다.</li>
 * </ul>
 */
@Component
public class CourseCellCacheMetrics {

    private static final String CELLS = "ghostrunner.course.cell.cache.cells";
    private static final String LOOKUPS = "ghostrunner.course.cell.cache.lookups";
    private static final String FILLS = "ghostrunner.course.cell.cache.fills";
    private static final String EVICTIONS = "ghostrunner.course.cell.cache.evictions";
    private static final String CANDIDATES = "ghostrunner.course.cell.cache.candidates";

    private static final String RESULT_TAG = "result";

    private final Counter cellHits;
    private final Counter cellMisses;
    private final Counter fullHitLookups;
    private final Counter partialHitLookups;
    private final Counter allMissLookups;
    private final Counter degradedLookups;
    private final Counter storedFills;
    private final Counter skippedOverLimitFills;
    private final Counter failedFills;
    private final Counter okEvictions;
    private final Counter readModelAbsentEvictions;
    private final Counter failedEvictions;
    private final DistributionSummary candidates;

    public CourseCellCacheMetrics(MeterRegistry registry) {
        this.cellHits = counter(registry, CELLS, "hit");
        this.cellMisses = counter(registry, CELLS, "miss");

        this.fullHitLookups = counter(registry, LOOKUPS, "full_hit");
        this.partialHitLookups = counter(registry, LOOKUPS, "partial_hit");
        this.allMissLookups = counter(registry, LOOKUPS, "all_miss");
        this.degradedLookups = counter(registry, LOOKUPS, "degraded");

        this.storedFills = counter(registry, FILLS, "stored");
        this.skippedOverLimitFills = counter(registry, FILLS, "skipped_over_limit");
        this.failedFills = counter(registry, FILLS, "failed");

        this.okEvictions = counter(registry, EVICTIONS, "ok");
        this.readModelAbsentEvictions = counter(registry, EVICTIONS, "read_model_absent");
        this.failedEvictions = counter(registry, EVICTIONS, "failed");

        this.candidates = DistributionSummary.builder(CANDIDATES)
                .description("지도 조회 1회의 반경 내 후보 코스 수")
                .register(registry);
    }

    /** 커버링 조회 1회에서 셀 단위로 히트·미스가 몇 개였는지. 둘의 비가 곧 DB 회피율이다. */
    public void recordCells(long hitCells, long missedCells) {
        cellHits.increment(hitCells);
        cellMisses.increment(missedCells);
    }

    /** 커버링 조회 1회의 결과. 요청 단위 지표라 셀 단위 {@link #recordCells}와 따로 센다. */
    public void recordLookup(LookupResult result) {
        switch (result) {
            case FULL_HIT -> fullHitLookups.increment();
            case PARTIAL_HIT -> partialHitLookups.increment();
            case ALL_MISS -> allMissLookups.increment();
            case DEGRADED -> degradedLookups.increment();
        }
    }

    /** 미스 셀 적재 시도의 결과. */
    public void recordFill(FillResult result) {
        switch (result) {
            case STORED -> storedFills.increment();
            case SKIPPED_OVER_LIMIT -> skippedOverLimitFills.increment();
            case FAILED -> failedFills.increment();
        }
    }

    /** 이빅트 시도의 결과. 리드모델 부재는 "비공개 코스 = 지도에 없음"이라 실패가 아니다. */
    public void recordEviction(EvictionResult result) {
        switch (result) {
            case OK -> okEvictions.increment();
            case READ_MODEL_ABSENT -> readModelAbsentEvictions.increment();
            case FAILED -> failedEvictions.increment();
        }
    }

    /** 요청 1회의 반경 내 후보 코스 수. */
    public void recordCandidates(int count) {
        candidates.record(count);
    }

    private static Counter counter(MeterRegistry registry, String name, String result) {
        return Counter.builder(name).tag(RESULT_TAG, result).register(registry);
    }

    public enum LookupResult {
        FULL_HIT, PARTIAL_HIT, ALL_MISS, DEGRADED
    }

    public enum FillResult {
        STORED, SKIPPED_OVER_LIMIT, FAILED
    }

    public enum EvictionResult {
        OK, READ_MODEL_ABSENT, FAILED
    }
}
