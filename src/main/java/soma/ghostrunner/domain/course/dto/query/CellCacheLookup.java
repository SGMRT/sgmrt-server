package soma.ghostrunner.domain.course.dto.query;

import soma.ghostrunner.domain.course.domain.GeoCell;

import java.util.List;

/**
 * 커버링 조회 1회의 결과. (설계: docs/design/course-cell-bucket-cache-design.md §3-4, D2)
 *
 * <p>셀 → 값 Map을 노출하지 않고 호출자가 실제로 묻는 세 가지만 준다 — 어떤 셀을 채워야 하는가(missedCells),
 * 이미 가진 카드는 무엇인가(cachedCourses), 캐시를 신뢰할 수 있는가(degraded).</p>
 *
 * <p><b>degraded는 "전 셀 미스"가 아니다.</b> 둘은 처리가 정반대다. 전 셀 미스는 미스 채움(조회 후 적재)이고,
 * degraded(Redis 장애)는 요청 단위 직행 강등이다. 장애 상황을 "전부 미스"로 뭉뚱그리면 장애 순간에 대형 채움 쿼리와
 * 재적재가 동시에 몰려 DB 부하가 증폭된다. 그래서 이 구분을 boolean 필드로 <b>타입에 강제</b>한다.</p>
 *
 * @param missedCells   값이 없거나 깨져 채워야 하는 셀. degraded면 커버링 전체다 (불변)
 * @param cachedCourses 히트한 셀들의 코스 카드를 모두 합친 것. 요청 반경 필터는 아직 적용되지 않았다 (불변)
 * @param degraded      Redis를 신뢰할 수 없어 이 요청은 직행 쿼리로 강등해야 한다
 */
public record CellCacheLookup(List<GeoCell> missedCells,
                              List<CourseMapDto> cachedCourses,
                              boolean degraded) {

    /** Redis를 신뢰할 수 없다 — 호출자는 채우지 말고 직행 쿼리로 강등해야 한다. */
    public static CellCacheLookup degraded(List<GeoCell> covering) {
        return new CellCacheLookup(List.copyOf(covering), List.of(), true);
    }

    /** 방문할 셀이 없다 — 채울 것도, 강등할 이유도 없다. */
    public static CellCacheLookup empty() {
        return new CellCacheLookup(List.of(), List.of(), false);
    }

    /** 커버링 전 셀이 히트했다 — 채움 쿼리 없이 응답할 수 있다. */
    public boolean fullHit() {
        return !degraded && missedCells.isEmpty();
    }
}
