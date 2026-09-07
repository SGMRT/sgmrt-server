package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.dao.CourseCellCache;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics.FillResult;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.domain.BoundingBox;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.domain.GeoDistance;
import soma.ghostrunner.domain.course.dto.query.CellBucket;
import soma.ghostrunner.domain.course.dto.query.CellCacheLookup;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 지도(주변 코스) 조회 유즈케이스 — 리드모델 읽기 진입점이자 캐시 판정 지점.
 *
 * <p>셀 버킷 캐시 설계 (docs/design/course-cell-bucket-cache-design.md §3-8, §3-13)</p>
 * <ul>
 *   <li><b>키 = 코스의 시작점 셀</b> — 캐시 단위는 요청자가 아니라 코스다(geohash p6).
 *       그래서 "넣은 곳 = 지울 곳"이 1:1로 맞고, 요청 커버링의 일부만 히트하는 <b>부분 채움</b>이 성립한다.</li>
 *   <li><b>캐시 값은 요청자와 무관한 "셀의 내용물"</b> — 그래서 <b>적재는 언제나 원 필터보다 앞</b>이다.
 *       요청 반경으로 자른 값을 적재하면, 같은 셀을 더 넓게 보는 다음 요청이 오답을 본다.</li>
 *   <li><b>강등은 한 곳으로 수렴</b> — 광역 요청·커버링 폭발·Redis 장애 <b>세 갈래</b>가 모두
 *       {@link #queryDirect}로 모인다. 강등 결과가 언제나 직행 경로와 같아야, 같은 요청이 Redis 상태에 따라
 *       다른 결과를 내지 않는다.</li>
 *   <li><b>두 경로가 같은 원 필터를 거친다</b> — 직행도 캐시 경로와 똑같이 {@link #withinRadius}로 마무리한다(파리티, 설계 D1).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseReadModelReader {

    /**
     * 캐시를 경유할 수 있는 최대 반경.
     *
     * 광역 요청은 커버링 셀이 급격히 늘어나는 데다 홈 기본 뷰가 아니라 캐시 재사용률도 낮다 — 직행이 낫다.
     */
    private static final int MAX_CACHEABLE_RADIUS_M = 3000;

    /**
     * 커버링 셀 수 상한.
     *
     * lat/lng에 검증 애노테이션이 없어 고위도 좌표가 그대로 들어올 수 있고, 그때 {@code cos(lat)}이 0에 수렴해
     * 경도 범위가 전 지구로 clamp된다. 판정은 열거 전에 {@link GeoCell#coveringCount} 산술로만 한다 [R1].
     */
    private static final int MAX_COVERING_CELLS = 128;

    private final CourseReadModelRepository readModelRepository;
    private final CourseCellCache cellCache;
    private final CourseCellCacheMetrics metrics;

    /**
     * 채움 쿼리와 직행 쿼리가 공유하는 LIMIT.
     *
     * <p>두 경로가 <b>같은 상한</b>을 써야 파리티가 성립한다. 직행만 낮은 상한(과거 50)을 두면 반경 안 코스가
     * 그 수를 넘는 순간 직행은 {@code ORDER BY start_lat}에 잘려 남쪽 코스만 후보가 되고, 같은 요청이
     * Redis 상태(강등 여부)에 따라 다른 모집단에서 랜덤 선별된다. 광역 요청(3km 초과)은 항상 직행이라
     * 이 편향이 평소에도 드러난다.</p>
     *
     * <p>상수가 아니라 주입값인 이유는 이 경로를 테스트하려고 코스를 수백 개 만들지 않기 위함이다(설계 D8).
     * 운영 튜닝 레버는 부수 효과다.</p>
     */
    @Value("${course.cache.cell-bucket.fill-limit:500}")
    private int cellFillLimit;

    /**
     * 요청 좌표 기준 반경 내 코스 카드 — 셀 버킷 캐시 경로.
     *
     * <p>커버링 셀을 MGET 1왕복으로 읽고, 미스 셀만 DB에서 채워 적재한 뒤, 캐시분과 채움분을 합쳐
     * 실좌표 원 필터로 마무리한다. 캐시를 쓸 수 없는 요청은 {@link #queryDirect}로 강등한다.</p>
     *
     * <p><b>강등 판정은 싼 것부터</b> — 광역(비교 1회) → 커버링 수(산술 [R1]) → Redis 장애(1왕복).
     * 걸러질 요청일수록 적은 비용으로 빠지고, 세 갈래가 모두 {@link #queryDirect} 하나로 수렴한다 (설계 §3-13).</p>
     */
    public List<CourseMapDto> findCoursesForMap(double lat, double lng, int radiusM) {
        if (radiusM > MAX_CACHEABLE_RADIUS_M) {          // 강등① 광역 요청
            return queryDirect(lat, lng, radiusM);
        }
        if (isCoveringTooLarge(lat, lng, radiusM)) {     // 강등② 커버링 폭발(극단 좌표)
            return queryDirect(lat, lng, radiusM);
        }

        CellCacheLookup lookup = cellCache.lookup(GeoCell.covering(lat, lng, radiusM));
        if (lookup.degraded()) {                         // 강등③ Redis 장애
            // 여기서 metrics.recordLookup을 부르지 않는다 — 이 강등은 CourseCellCache가 이미 기록했다.
            // 다시 세면 이중 계수로 degraded 비율이 부풀어 장애 신호가 오염된다.
            return queryDirect(lat, lng, radiusM);
        }
        return withinRadius(candidatesOf(lookup), lat, lng, radiusM);
    }

    /**
     * 캐시를 경유하지 않는 직행 경로. 강등 3갈래가 전부 여기로 수렴한다.
     *
     * <p>원 필터와 LIMIT을 캐시 경로와 <b>똑같이</b> 적용한다 — 그래야 강등 결과와 캐시 결과가 같다.
     * Redis 장애 강등은 요청 중에도 일어날 수 있으므로, 두 경로가 다른 결과를 내면 같은 요청의 응답이
     * Redis 상태에 따라 흔들린다.</p>
     */
    private List<CourseMapDto> queryDirect(double lat, double lng, int radiusM) {
        BoundingBox bounds = BoundingBox.of(lat, lng, radiusM);
        List<CourseMapDto> rows = readModelRepository.findCoursesForMap(
                bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng(), cellFillLimit);

        return withinRadius(rows, lat, lng, radiusM);
    }

    /**
     * [R1] 커버링을 <b>열거하기 전에</b> 개수만으로 판정한다.
     *
     * <p>{@link GeoCell#coveringCount}는 셀을 만들지 않는 O(1) 산술이고 {@link GeoCell#covering}은 셀 수만큼 객체를 만든다.
     * 가드가 필요한 그 좌표(고위도 → 경도 범위가 전 지구로 clamp)에서 수십만 셀을 할당한 뒤 버리지 않으려면
     * 판정이 열거보다 앞서야 한다.</p>
     */
    private boolean isCoveringTooLarge(double lat, double lng, int radiusM) {
        long coveringCount = GeoCell.coveringCount(lat, lng, radiusM);
        if (coveringCount <= MAX_COVERING_CELLS) {
            return false;
        }

        log.warn("CourseReadModelReader - covering cells over limit({}), degrade to direct query. cells={}, lat={}, lng={}, radiusM={}",
                MAX_COVERING_CELLS, coveringCount, lat, lng, radiusM);
        return true;
    }

    /**
     * 히트 셀의 카드와 미스 셀을 채운 카드를 합쳐 <b>원 필터 이전의</b> 후보를 만든다.
     *
     * <p>적재는 이 단계 안(=필터 앞)에서 끝난다. 원 필터를 먼저 걸고 적재하면 캐시에 "요청 반경으로 잘린 셀"이
     * 실려, 같은 셀을 더 넓게 보는 다음 요청이 오답을 받는다.</p>
     *
     * <p>히트분은 불변 리스트라 새 리스트에 담는다.</p>
     */
    private List<CourseMapDto> candidatesOf(CellCacheLookup lookup) {
        if (lookup.fullHit()) {          // 전 셀 히트 — DB를 아예 건드리지 않는 경로
            return lookup.cachedCourses();
        }

        List<CourseMapDto> candidates = new ArrayList<>(lookup.cachedCourses());
        candidates.addAll(fillMissedCells(lookup.missedCells()));
        return candidates;
    }

    /**
     * 미스 셀을 합집합 박스 <b>1회</b> 조회로 채우고 적재한다.
     *
     * <p>미스 셀이 흩어져 있어도 쿼리는 한 번이다. 박스에 딸려온 히트 셀 소속 행은 {@link #groupByStartCell}이
     * 버리므로 히트 셀의 캐시 값과 충돌하지 않는다.</p>
     *
     * <p>반환이 {@code rows}가 아니라 <b>버킷의 평면화</b>인 이유도 같다 — rows에는 히트 셀 소속 행이 섞여 있어
     * 그대로 합치면 캐시분과 중복된다.</p>
     */
    private List<CourseMapDto> fillMissedCells(List<GeoCell> missedCells) {
        BoundingBox bounds = GeoCell.enclosingBox(missedCells);
        List<CourseMapDto> rows = readModelRepository.findCoursesForMap(
                bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng(), cellFillLimit);
        List<CellBucket> buckets = groupByStartCell(rows, missedCells);

        cacheUnlessTruncated(buckets, rows.size());
        return buckets.stream().flatMap(bucket -> bucket.courses().stream()).toList();
    }

    /**
     * 채움 결과를 적재한다 — 단, 채움 쿼리가 LIMIT에 걸렸으면 <b>전체 스킵</b>한다 (설계 결정 3).
     *
     * <p>잘린 결과는 공간적으로 편향돼 있다(ORDER BY start_lat, start_lng이라 북쪽이 잘린다). 이번 응답에 쓰는 것은
     * 손해가 작지만, 그 편향된 값이 TTL 동안 캐시에 각인되면 이후 요청 전부가 같은 오답을 본다.</p>
     */
    private void cacheUnlessTruncated(List<CellBucket> buckets, int fetchedRowCount) {
        if (fetchedRowCount >= cellFillLimit) {
            log.warn("CourseReadModelReader - cell fill limit reached ({}), skip caching for this request", cellFillLimit);
            metrics.recordFill(FillResult.SKIPPED_OVER_LIMIT);
            return;
        }
        cellCache.putAll(buckets);
    }

    /**
     * 조회 행을 시작점 셀로 분류한다.
     *
     * <p>미스 셀은 코스가 없어도 <b>빈 버킷으로 선초기화</b>한다 — 적재하지 않으면 그 셀은 TTL 내내 영구 미스가 되어
     * 외곽 요청이 매번 채움 쿼리를 돌린다(네거티브 캐싱).</p>
     */
    private static List<CellBucket> groupByStartCell(List<CourseMapDto> rows, List<GeoCell> missedCells) {
        Map<GeoCell, List<CourseMapDto>> grouped = new LinkedHashMap<>();
        missedCells.forEach(cell -> grouped.put(cell, new ArrayList<>()));

        for (CourseMapDto row : rows) {
            List<CourseMapDto> bucket = grouped.get(GeoCell.of(row.startLat(), row.startLng()));
            if (bucket != null) {
                bucket.add(row);   // 히트 셀 소속 행은 버린다 — 그 셀의 캐시 값이 이미 정답이다
            }
        }
        return grouped.entrySet().stream()
                .map(entry -> new CellBucket(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 후보를 요청 반경의 원 안으로 좁힌다 — 캐시 경로와 직행 경로가 공유하는 마지막 단계다.
     *
     * <p>최종 판정은 매 요청 실좌표로 한다. 셀 스냅 오차(셀 단위로 넓게 잡힌 후보)가 응답에 남지 않는 이유다.</p>
     */
    private List<CourseMapDto> withinRadius(List<CourseMapDto> candidates, double lat, double lng, int radiusM) {
        List<CourseMapDto> coursesInRadius = candidates.stream()
                .filter(course -> GeoDistance.withinRadius(lat, lng, course.startLat(), course.startLng(), radiusM))
                .toList();

        metrics.recordCandidates(coursesInRadius.size());
        return coursesInRadius;
    }
}
