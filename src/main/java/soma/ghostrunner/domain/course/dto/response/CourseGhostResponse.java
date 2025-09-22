package soma.ghostrunner.domain.course.dto.response;

import java.time.LocalDateTime;

public record CourseGhostResponse (
    String runnerUuid,
    String runnerProfileUrl,
    String runnerNickname,

    Long runningId,
    String runningName,
    Double averagePace,
    Integer cadence,
    Integer bpm,
    Long duration,
    Boolean isPublic,
    LocalDateTime startedAt
) {}
