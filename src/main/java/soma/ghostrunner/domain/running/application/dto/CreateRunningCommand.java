package soma.ghostrunner.domain.running.application.dto;

import java.util.List;

public record CreateRunningCommand(
        String runningName,
        Long ghostRunningId,
        String mode,
        Long startedAt,
        RunRecordCommand record,
        Boolean hasPaused,
        Boolean isPublic,
        List<TelemetryCommand> telemetries
) {}
