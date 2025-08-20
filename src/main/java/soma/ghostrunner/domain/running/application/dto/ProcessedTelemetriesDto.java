package soma.ghostrunner.domain.running.application.dto;

import java.util.List;

public record ProcessedTelemetriesDto(
        List<TelemetryDto> relativeTelemetries, CoordinateDto startPoint,
        Double highestPace, Double lowestPace,
        Double avgElevation) {

}
