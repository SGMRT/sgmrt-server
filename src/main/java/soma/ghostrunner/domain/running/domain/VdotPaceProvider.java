package soma.ghostrunner.domain.running.domain;

import soma.ghostrunner.domain.running.infra.dto.VdotPaceDto;

import java.util.List;

public interface VdotPaceProvider {

    Double getPaceByVdotAndRunningType(int vdot, RunningType runningType);

    List<VdotPaceDto> getVdotPaceByVdot(int vdot);

}
