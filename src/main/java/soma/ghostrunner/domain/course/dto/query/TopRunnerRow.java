package soma.ghostrunner.domain.course.dto.query;

/**
 * TOP4 재계산용 집계 행 (CQRS Query)
 *
 * 목적:
 * - 코스별 "멤버 단위 최고 기록" 집계 결과 매핑
 * - Native Query Interface Projection
 *
 * 한 행 = 한 멤버의 해당 코스 최고 기록.
 *
 * 설계 문서: docs/refactoring/course-read-model/core/04-detailed-design.md §3-3
 */
public interface TopRunnerRow {

    /**
     * @return 러너(멤버) ID
     */
    Long getMemberId();

    /**
     * @return 해당 멤버의 코스 최고 기록 (초)
     */
    Integer getBestDurationSeconds();
}
