package soma.ghostrunner.domain.running.application.dto.request;

public record RunRecordCommand(
        Double distance,
        Integer elevationGain,
        Integer elevationLoss,
        Long duration,
        Double avgPace,
        Integer calories,
        Integer avgBpm,
        Integer avgCadence
) {}
