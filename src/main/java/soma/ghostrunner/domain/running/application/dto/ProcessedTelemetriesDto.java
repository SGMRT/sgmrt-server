package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.course.domain.StartPoint;

import java.util.List;

@Getter
@AllArgsConstructor @Builder
public class ProcessedTelemetriesDto {
    private List<TelemetryDto> relativeTelemetries;
    private StartPoint startPoint;
    private String courseCoordinates;
    private Double highestPace;
    private Double lowestPace;
}
