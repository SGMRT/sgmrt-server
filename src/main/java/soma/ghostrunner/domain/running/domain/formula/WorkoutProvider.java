package soma.ghostrunner.domain.running.domain.formula;

import java.util.List;

public interface WorkoutProvider {

    List<Workout> findWorkoutTemplates(WorkoutType type);

}
