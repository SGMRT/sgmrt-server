package soma.ghostrunner.domain.running.application.dto;

public record TelemetryCommand(
        Long timeStamp,
        Double lat,
        Double lng,
        Double dist,
        Double pace,
        Integer alt,
        Integer cadence,
        Integer bpm,
        Boolean isRunning
) {
    public TelemetryCommand convertToRelativeTs(Long startedAt) {
        return new TelemetryCommand(
                this.timeStamp - startedAt,
                this.lat,
                this.lng,
                this.dist,
                this.pace,
                this.alt,
                this.cadence,
                this.bpm,
                this.isRunning
        );
    }
}
