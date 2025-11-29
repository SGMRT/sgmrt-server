package soma.ghostrunner.domain.running.domain.path;

import java.util.List;

public record SimplifiedPaths(List<Coordinates> simplifiedCoordinates, List<Checkpoint> checkpoints) {}
