package soma.ghostrunner.domain.running.domain.path;

import java.util.List;

public record TelemetryStatistics(
        List<Telemetry> relativeTelemetries, Coordinates startPoint,
        Double highestPace, Double lowestPace,
        Double avgElevation, Double courseDistance) {

}
