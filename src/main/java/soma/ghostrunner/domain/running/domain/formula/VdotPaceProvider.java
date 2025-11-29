package soma.ghostrunner.domain.running.domain.formula;

import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.List;

public interface VdotPaceProvider {

    Double getPaceByVdotAndRunningType(int vdot, RunningType runningType);

    List<VdotPace> getVdotPaceByVdot(int vdot);

}
