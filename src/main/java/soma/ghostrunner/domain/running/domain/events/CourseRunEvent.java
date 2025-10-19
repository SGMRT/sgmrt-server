package soma.ghostrunner.domain.running.domain.events;

public record CourseRunEvent (
        Long courseId,
        String courseName,
        Long courseOwnerId,
        Long runningId,
        Long runnerId,
        String runnerNickname
) {}
