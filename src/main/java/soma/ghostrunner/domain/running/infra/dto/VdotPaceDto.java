package soma.ghostrunner.domain.running.infra.dto;

import soma.ghostrunner.domain.running.domain.RunningType;

public record VdotPaceDto(RunningType type, Double pacePerKm) {

}
