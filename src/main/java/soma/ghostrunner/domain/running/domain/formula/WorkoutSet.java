package soma.ghostrunner.domain.running.domain.formula;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.Map;

@Getter
@AllArgsConstructor
public class WorkoutSet {

    Integer setNum;
    WorkoutType type;
    UnitType unit;
    double value;

    public enum UnitType {
        DURATION, DISTANCE
    }

    public double convertToDistance(Map<RunningType, Double> paces) {
        if (unit == WorkoutSet.UnitType.DISTANCE) {
            return value;
        }
        // 미터(m) 반환
        double pace = paces.get(RunningType.valueOf(type.name()));
        return (value / pace) * 1000;
    }

}
