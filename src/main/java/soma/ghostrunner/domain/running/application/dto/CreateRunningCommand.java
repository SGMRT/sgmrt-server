package soma.ghostrunner.domain.running.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CreateRunningCommand(
        Long ghostRunningId,
        String mode,
        LocalDateTime startedAt,
        RunRecordCommand record,
        Boolean hasPaused,
        Boolean isPublic,
        List<TelemetryCommand> telemetries
) {}
