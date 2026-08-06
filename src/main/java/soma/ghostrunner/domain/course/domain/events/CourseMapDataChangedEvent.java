package soma.ghostrunner.domain.course.domain.events;

/**
 * 지도에 노출되는 코스 데이터(노출 여부 또는 카드 내용)가 바뀌었다.
 *
 * <p>좌표를 동봉하는 이유 — 코스 삭제는 커밋 후 리드모델이 남아 있지 않아 {@code courseId}만으로는
 * 어느 셀을 이빅트해야 하는지 역산할 수 없다(M2). 그래서 이벤트가 좌표를 실어 나른다.
 * 좌표는 발행 시점 트랜잭션 안에서 이미 로드된 {@code Course}에서 얻으므로 추가 쿼리가 없다.
 *
 * <p>생성은 {@code Course#createMapDataChangedEvent()}가 유일한 경로다.
 * 시작점이 없는 코스라면 좌표가 {@code null}일 수 있고, 그때는 이빅트할 셀을 정할 수 없으므로
 * 구독자가 건너뛴다.
 *
 * <p>설계 문서: docs/design/course-cell-bucket-cache-design.md §3-5 · §4
 */
public record CourseMapDataChangedEvent(Long courseId, Double startLat, Double startLng) {
}
