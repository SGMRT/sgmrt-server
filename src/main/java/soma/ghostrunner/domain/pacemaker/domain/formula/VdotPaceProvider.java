package soma.ghostrunner.domain.pacemaker.domain.formula;

import soma.ghostrunner.domain.pacemaker.domain.RunningType;

import java.util.List;

public interface VdotPaceProvider {

    Double getPaceByVdotAndRunningType(int vdot, RunningType runningType);

    List<VdotPace> getVdotPaceByVdot(int vdot);

}
