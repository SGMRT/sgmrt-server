package soma.ghostrunner.domain.course.dto;

import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;

public record RunnerProfile(
    String uuid,
    String profileUrl
) {
    public static RunnerProfile from(CourseGhostResponse ghost) {
        return new RunnerProfile(
            ghost.runnerUuid(),
            ghost.runnerProfileUrl()
        );
    }

    public static RunnerProfile from(CourseRunDto run) {
        return new RunnerProfile(
            run.runnerUuid(),
            run.runnerProfileUrl()
        );
    }
}
