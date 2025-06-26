package soma.ghostrunner.domain.running.application.dto;

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
