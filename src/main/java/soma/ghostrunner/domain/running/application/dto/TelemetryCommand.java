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
    public TelemetryCommand withTimeStamp(Long newTimeStamp) {
        return new TelemetryCommand(
                newTimeStamp,
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
