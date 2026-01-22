package soma.ghostrunner.domain.pacemaker.domain.formula;

import java.util.List;

public interface WorkoutProvider {

    List<Workout> findWorkoutTemplates(WorkoutType type);

}
