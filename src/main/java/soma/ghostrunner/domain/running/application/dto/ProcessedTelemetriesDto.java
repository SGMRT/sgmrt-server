package soma.ghostrunner.domain.running.application.dto;

import java.util.List;

public record ProcessedTelemetriesDto(
        List<TelemetryDto> relativeTelemetries, CoordinateDto startPoint,
        List<CoordinateDto> coordinates, Double highestPace, Double lowestPace, Double avgElevation) {

}
