package soma.ghostrunner.domain.pacemaker.domain.events;

public record PacemakerCreatedEvent (
        Long pacemakerId,
        Long courseId,
        String memberUuid
) {}
