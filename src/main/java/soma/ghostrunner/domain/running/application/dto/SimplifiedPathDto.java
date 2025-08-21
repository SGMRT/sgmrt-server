package soma.ghostrunner.domain.running.application.dto;

import java.util.List;

public record SimplifiedPathDto(List<CoordinateDto> simplifiedCoordinates, List<CheckpointDto> checkpoints) {}
