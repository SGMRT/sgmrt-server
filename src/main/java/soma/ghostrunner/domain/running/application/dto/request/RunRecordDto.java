package soma.ghostrunner.domain.running.application.dto.request;

public record RunRecordDto(
        Double distance,
        Integer elevationGain,
        Integer elevationLoss,
        Long duration,
        Double avgPace,
        Integer calories,
        Integer avgBpm,
        Integer avgCadence
) {}
