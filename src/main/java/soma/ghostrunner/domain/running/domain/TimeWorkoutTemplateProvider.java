package soma.ghostrunner.domain.running.domain;

import java.util.Map;

public interface TimeWorkoutTemplateProvider {

    TimeWorkoutTemplate findBestFitWorkoutTemplate(RunningType runningType, double goalDistance, Map<RunningType, Double> expectedPaces);

}
