package soma.ghostrunner.domain.running.domain;

import java.util.List;

public interface VdotPaceProvider {

    Double getPaceByVdotAndRunningType(int vdot, RunningType runningType);

}
