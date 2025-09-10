package soma.ghostrunner.domain.running.domain.formula;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class WorkoutTemplate {

    private final String id;
    private final WorkoutType workoutType;
    private final List<WorkoutSet> sets;

    public double calculateTotalDistance(Map<RunningType, Double> paces) {
        return sets.stream()
                .mapToDouble(set -> set.convertToDistance(paces))
                .sum();
    }

}
