package soma.ghostrunner.domain.course.dto.query;

import soma.ghostrunner.domain.course.domain.GeoCell;

import java.util.List;

/**
 * 한 셀의 적재 단위 — "이 셀에 시작점을 둔 코스 전부".
 * (설계: docs/design/course-cell-bucket-cache-design.md §3-4)
 *
 * <p>값은 요청자와 무관한 "셀의 내용물"이다. 요청 반경으로 자른 목록을 넣으면 다음 요청이 오답을 받는다.</p>
 *
 * <p><b>빈 셀도 빈 리스트로 존재한다(네거티브 캐싱).</b> 코스가 없는 셀을 적재하지 않으면 그 셀은 TTL 내내
 * 영구 미스가 되어, 도심 외곽 요청이 매번 채움 쿼리를 돌린다. "코스가 없음"도 캐시할 값이다.</p>
 *
 * @param cell    적재 대상 셀. 그대로 캐시 키가 된다
 * @param courses 이 셀에 시작점을 둔 코스 카드 전부. 코스가 없으면 빈 리스트다
 */
public record CellBucket(GeoCell cell, List<CourseMapDto> courses) {
}
