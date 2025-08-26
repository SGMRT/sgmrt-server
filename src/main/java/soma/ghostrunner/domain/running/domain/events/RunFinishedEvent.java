package soma.ghostrunner.domain.running.domain.events;

// TODO : 러닝 ID를 포함한 Observer 를 주체로 이벤트를 발행하도록 수정, 외부로 발행할 때는 runId -> 내부로 발행할 떄는 뚱뚱하게
public record RunFinishedEvent(
        Long runId,
        String memberUuid,
        Double averagePace
) {
}
