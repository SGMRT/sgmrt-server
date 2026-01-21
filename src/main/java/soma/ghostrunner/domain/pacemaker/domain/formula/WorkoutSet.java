package soma.ghostrunner.domain.pacemaker.domain.formula;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;

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
        if (type.name().equals("X")) {
            return 0.0;
        }
        double pace = paces.get(RunningType.valueOf(type.name()));
        return (value / pace) * 1000;
    }

}
