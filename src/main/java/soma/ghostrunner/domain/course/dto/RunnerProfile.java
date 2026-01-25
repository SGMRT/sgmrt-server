package soma.ghostrunner.domain.course.dto;

import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;

public record RunnerProfile(
    String uuid,
    String profileUrl,
    Integer recordTimeSeconds  // 추가: 리드모델 조회용
) {
    public static RunnerProfile from(CourseGhostResponse ghost) {
        return new RunnerProfile(
            ghost.runnerUuid(),
            ghost.runnerProfileUrl(),
            null  // 기존 호환성 유지
        );
    }

    public static RunnerProfile from(CourseRunDto run) {
        return new RunnerProfile(
            run.runnerUuid(),
            run.runnerProfileUrl(),
            null  // 기존 호환성 유지
        );
    }
}
