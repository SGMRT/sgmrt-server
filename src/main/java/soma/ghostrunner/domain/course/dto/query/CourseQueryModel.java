package soma.ghostrunner.domain.course.dto.query;

import java.util.List;

public record CourseQueryModel (
    Long id,
    String name,
    List<RunnerSummary> topRunners,
    int runnerCount
) {}
