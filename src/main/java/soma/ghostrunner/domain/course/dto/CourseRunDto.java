package soma.ghostrunner.domain.course.dto;

public record CourseRunDto (
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
    Long startedAt
) {}
