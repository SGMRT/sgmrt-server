package soma.ghostrunner.domain.running.domain.formula;

import java.util.List;

public interface WorkoutTemplateProvider {

    List<Workout> findWorkoutTemplates(WorkoutType type);

}
