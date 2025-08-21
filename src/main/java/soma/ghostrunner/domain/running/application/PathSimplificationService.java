package soma.ghostrunner.domain.running.application;

import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.domain.PathSimplifier;

import java.util.List;

// TODO : Path 라는 도메인을 분리하여 시계열을 가공하는 알고리즘을 모아두는 방안 고려
@Component
public class PathSimplificationService {

    public SimplifiedPathDto simplify(ProcessedTelemetriesDto processedTelemetries) {
        List<CoordinateDtoWithTs> telemetryCoordinates = CoordinateDtoWithTs.toCoordinateDtosWithTsList(processedTelemetries.relativeTelemetries());

        List<CoordinateDto> simplifiedCoordinates = PathSimplifier.simplifyToRenderingTelemetries(telemetryCoordinates);
        List<CoordinateDto> edgePoints = PathSimplifier.extractEdgePoints(telemetryCoordinates);
        List<CheckpointDto> checkpoints = PathSimplifier.calculateAngles(edgePoints);

        return new SimplifiedPathDto(simplifiedCoordinates, checkpoints);
    }

}
