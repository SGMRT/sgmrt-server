package soma.ghostrunner.domain.running.application.dto.request;

import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.util.List;

public record CreateRunCommand(
        String runningName,
        Long ghostRunningId,
        String mode,
        Long startedAt,
        RunRecordDto record,
        Boolean hasPaused,
        Boolean isPublic,
        List<TelemetryDto> telemetries
) {}
