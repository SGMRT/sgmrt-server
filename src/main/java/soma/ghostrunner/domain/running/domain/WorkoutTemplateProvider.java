package soma.ghostrunner.domain.running.domain;

import java.util.Map;

public interface WorkoutTemplateProvider {

    WorkoutTemplate findBestFitWorkoutTemplate(RunningType runningType, double goalDistance, Map<RunningType, Double> expectedPaces);

}
