package soma.ghostrunner.domain.running.application.dto;

import java.time.LocalDateTime;

public record TelemetryCommand(
        LocalDateTime timeStamp,
        Double lat,
        Double lng,
        Double dist,
        Double pace,
        Integer alt,
        Integer cadence,
        Integer bpm,
        Boolean isRunning
) {}
