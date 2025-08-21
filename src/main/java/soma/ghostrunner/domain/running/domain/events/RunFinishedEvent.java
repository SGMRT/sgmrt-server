package soma.ghostrunner.domain.running.domain.events;

public record RunFinishedEvent(
        String memberUuid,
        Double averagePace
) {
}
