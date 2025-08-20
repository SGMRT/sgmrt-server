package soma.ghostrunner.domain.running.domain.events;

public record RunFinishedEvent(
        Long userId,
        Long runId
) {
}
