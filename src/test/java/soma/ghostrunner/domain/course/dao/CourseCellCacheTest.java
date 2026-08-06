package soma.ghostrunner.domain.course.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.dto.query.CellBucket;
import soma.ghostrunner.domain.course.dto.query.CellCacheLookup;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.global.config.CacheType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀 버킷 캐시의 Redis 왕복 계약 검증. (설계: docs/design/course-cell-bucket-cache-design.md §3-4, §3-6, §6 테스트 6·7)
 *
 * <p>이 어댑터가 지켜야 할 계약은 두 가지다.
 * <ul>
 *     <li><b>값 계약</b> — 캐시를 거친 카드는 DB에서 온 카드와 구별되지 않아야 한다(25필드 전부 보존).
 *         빈 셀은 "값이 빈 배열"로 저장되어 히트로 판정된다 — 네거티브 캐싱이 없으면 코스 없는 셀이
 *         TTL 내내 영구 미스가 되어 매 요청이 채움 쿼리를 돈다.</li>
 *     <li><b>셀 단위 방어</b> — 값 하나가 깨져도 그 셀만 미스다. 전체 미스로 번지면 깨진 값 하나가
 *         요청 전체를 대형 채움 쿼리로 몰아넣는다.</li>
 * </ul>
 */
@DisplayName("CourseCellCache 통합 테스트 - 셀 버킷 Redis 왕복")
class CourseCellCacheTest extends IntegrationTestSupport {

    @Autowired
    CourseCellCache cellCache;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /** 이 캐시가 쓰는 Redis 키 전부를 훑는 패턴. DatabaseCleanserExtension은 Redis를 건드리지 않는다. */
    private static final String CELL_KEY_PATTERN = CacheType.Names.COURSE_CELLS + "*";

    /** 설계상 TTL 상한 — 이빅트 밖 변경의 스테일 상한이자, 적재 시 만료가 반드시 걸렸음을 확인하는 기준이다. */
    private static final long TTL_SECONDS = 600L;

    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;

    @BeforeEach
    void clearCellCache() {
        Set<String> keys = stringRedisTemplate.keys(CELL_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @DisplayName("적재한 코스 카드는 25필드 그대로 복원되고, 빈 배열을 넣은 셀도 히트로 판정되며, 키에는 TTL 600초가 걸린다")
    @Test
    void putAll_ThenLookup_PreservesEveryFieldAndCachesEmptyCell() {
        // given : 코스가 있는 셀 하나와, 코스가 하나도 없는 셀 하나
        List<GeoCell> cells = seoulCells();
        GeoCell occupiedCell = cells.get(0);
        GeoCell emptyCell = cells.get(1);
        CourseMapDto card = fullyPopulatedCard(1L);

        // when
        cellCache.putAll(List.of(
                new CellBucket(occupiedCell, List.of(card)),
                new CellBucket(emptyCell, List.of())));
        CellCacheLookup lookup = cellCache.lookup(List.of(occupiedCell, emptyCell));

        // then : 라운드트립에서 전 필드가 보존된다 (record equals가 25개 컴포넌트를 모두 비교한다)
        assertThat(lookup.cachedCourses()).containsExactly(card);
        assertThat(lookup.cachedCourses().get(0))
                .usingRecursiveComparison()
                .isEqualTo(card);

        // then : 빈 배열을 저장한 셀도 히트다 — 미스로 새면 그 셀은 TTL 내내 채움 쿼리를 유발한다
        assertThat(lookup.missedCells()).isEmpty();
        assertThat(lookup.degraded()).isFalse();

        // then : 만료 없이 각인되지 않는다
        Long ttl = stringRedisTemplate.getExpire(cacheKey(occupiedCell), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(TTL_SECONDS);
    }

    @DisplayName("값이 깨진 셀만 미스가 되고 나머지 셀은 그대로 히트하며, 예외는 밖으로 나가지 않는다")
    @Test
    void lookup_TreatsBrokenValueAsSingleCellMiss() {
        // given : 세 셀을 적재한 뒤 가운데 셀의 값만 파싱 불가능한 문자열로 덮어쓴다
        List<GeoCell> cells = seoulCells();
        GeoCell intactCell = cells.get(0);
        GeoCell brokenCell = cells.get(1);
        GeoCell anotherIntactCell = cells.get(2);

        CourseMapDto intactCard = fullyPopulatedCard(1L);
        CourseMapDto anotherIntactCard = fullyPopulatedCard(2L);

        cellCache.putAll(List.of(
                new CellBucket(intactCell, List.of(intactCard)),
                new CellBucket(brokenCell, List.of(fullyPopulatedCard(3L))),
                new CellBucket(anotherIntactCell, List.of(anotherIntactCard))));
        stringRedisTemplate.opsForValue().set(cacheKey(brokenCell), "[{\"courseId\":");

        // when
        CellCacheLookup lookup = cellCache.lookup(cells);

        // then : 역직렬화 실패는 그 셀 하나의 미스로만 강등된다
        assertThat(lookup.missedCells()).containsExactly(brokenCell);
        assertThat(lookup.cachedCourses()).containsExactlyInAnyOrder(intactCard, anotherIntactCard);

        // then : Redis가 살아 있으므로 강등(직행)이 아니라 부분 히트다.
        //        lookup이 값을 반환했다는 사실 자체가 역직렬화 예외 미전파의 증거다.
        assertThat(lookup.degraded()).isFalse();
    }

    /** 서울 반경 1km를 덮는 실제 커버링 셀 — 서로 다른 셀 3개를 쓰기 위한 픽스처다. */
    private List<GeoCell> seoulCells() {
        return GeoCell.covering(SEOUL_LAT, SEOUL_LNG, 1000).subList(0, 3);
    }

    private String cacheKey(GeoCell cell) {
        return CacheType.Names.COURSE_CELLS + "::" + cell.id();
    }

    /** 25필드를 전부 서로 다른 값으로 채운 카드. 한 필드라도 누락되면 라운드트립 비교에서 드러난다. */
    private CourseMapDto fullyPopulatedCard(long courseId) {
        return new CourseMapDto(
                courseId,
                "남산 순환 코스 " + courseId,
                "owner-uuid-" + courseId,
                "USER",
                "https://cdn.ghostrunner.io/routes/" + courseId + ".json",
                "https://cdn.ghostrunner.io/thumbnails/" + courseId + ".png",
                7.42,
                123.4,
                56.7,
                45.6,
                SEOUL_LAT,
                SEOUL_LNG,
                17L,
                1801,
                "top1-uuid",
                "https://cdn.ghostrunner.io/profiles/top1.png",
                1902,
                "top2-uuid",
                "https://cdn.ghostrunner.io/profiles/top2.png",
                2003,
                "top3-uuid",
                "https://cdn.ghostrunner.io/profiles/top3.png",
                2104,
                "top4-uuid",
                "https://cdn.ghostrunner.io/profiles/top4.png");
    }
}
