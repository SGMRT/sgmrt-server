package soma.ghostrunner.domain.running.application.dto.request;

public record CreateRunCommand(
        String runningName,
        Long ghostRunningId,
        String mode,
        Long startedAt,
        RunRecordDto record,
        Boolean hasPaused,
        Boolean isPublic
) {}
