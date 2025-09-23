package soma.ghostrunner.domain.running.application;

import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.running.domain.path.*;

import java.util.List;

@Service
public class PathSimplificationService {

    public SimplifiedPaths simplify(TelemetryStatistics processedTelemetries) {

        List<CoordinatesWithTs> telemetryCoordinates = CoordinatesWithTs.toCoordinatesWithTsList(processedTelemetries.relativeTelemetries());

        List<Coordinates> simplifiedCoordinates = PathSimplifier.simplifyToRenderingTelemetries(telemetryCoordinates);
        List<Coordinates> edgePoints = PathSimplifier.extractEdgePoints(telemetryCoordinates);
        List<Checkpoint> checkpoints = PathSimplifier.calculateAngles(edgePoints);

        return new SimplifiedPaths(simplifiedCoordinates, checkpoints);
    }

}
