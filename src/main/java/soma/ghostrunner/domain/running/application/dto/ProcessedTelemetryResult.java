package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import soma.ghostrunner.domain.course.domain.StartPoint;

import java.util.List;

@Data @Builder
@AllArgsConstructor
public class ProcessedTelemetryResult {
    private List<TelemetryCommand> relativeTelemetries;
    private StartPoint startPoint;
    private String courseCoordinates;
}
