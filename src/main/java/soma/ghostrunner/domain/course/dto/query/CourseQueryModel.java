package soma.ghostrunner.domain.course.dto.query;

import soma.ghostrunner.domain.course.dto.RunnerProfile;

import java.util.List;

public record CourseQueryModel (
    Long id,
    String name,
    List<RunnerProfile> topRunners,
    int runnerCount
) {}
