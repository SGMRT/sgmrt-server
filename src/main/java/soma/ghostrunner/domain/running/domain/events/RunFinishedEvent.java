package soma.ghostrunner.domain.running.domain.events;

public record RunFinishedEvent(
        Long runId,
        String memberUuid,
        Double averagePace
) {
}
