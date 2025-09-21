package soma.ghostrunner.domain.running.application;

import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.running.domain.path.*;

import java.util.List;

// TODO : Path 라는 도메인을 분리하여 시계열을 가공하는 알고리즘을 모아두는 방안 고려
@Component
public class PathSimplificationService {

    public SimplifiedPaths simplify(TelemetryStatistics processedTelemetries) {

        List<CoordinatesWithTs> telemetryCoordinates = CoordinatesWithTs.toCoordinatesWithTsList(processedTelemetries.relativeTelemetries());

        List<Coordinates> simplifiedCoordinates = PathSimplifier.simplifyToRenderingTelemetries(telemetryCoordinates);
        List<Coordinates> edgePoints = PathSimplifier.extractEdgePoints(telemetryCoordinates);
        List<Checkpoint> checkpoints = PathSimplifier.calculateAngles(edgePoints);

        return new SimplifiedPaths(simplifiedCoordinates, checkpoints);
    }

}
