package soma.ghostrunner.domain.running.application.dto;

public record CheckpointDto (
    Double lat,
    Double lng,
    Integer angle
) {}
