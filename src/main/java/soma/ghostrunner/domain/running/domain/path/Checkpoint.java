package soma.ghostrunner.domain.running.domain.path;

public record Checkpoint(
    Double lat,
    Double lng,
    Integer angle
) {}
