package soma.ghostrunner.domain.running.domain.events;

public record PacemakerCreatedEvent (
        Long pacemakerId,
        Long courseId,
        String memberUuid
) {}
