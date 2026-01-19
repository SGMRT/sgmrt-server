package soma.ghostrunner.domain.running.domain.events;

public record RunFinishedEvent(
        Long runId,
        Long courseId,
        String memberUuid,
        Long memberId,         // 추가: 리드모델 동기화용
        Integer durationSeconds, // 추가: 리드모델 동기화용
        Double averagePace
) {
}
