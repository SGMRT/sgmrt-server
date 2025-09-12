package soma.ghostrunner.domain.running.domain.path;

import java.util.List;

public record SimplifiedPath(List<Coordinates> simplifiedCoordinates, List<Checkpoint> checkpoints) {}
