package soma.ghostrunner.domain.running.domain.events;

import soma.ghostrunner.domain.course.domain.Course;

public record RunFinishedEvent(
        Long runId,
        Long courseId,
        String memberUuid,
        Double averagePace,
        Course course,
        Boolean hasPaused
) {
}
