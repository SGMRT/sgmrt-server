package soma.ghostrunner.domain.running.domain.events;

public record RunUpdatedEvent (
        Long runId,
        Long courseId,
        String memberUuid,
        String runName,
        Boolean isPublic
) {}
