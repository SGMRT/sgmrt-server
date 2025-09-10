package soma.ghostrunner.domain.running.domain.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class WorkoutTemplateTest {

    @DisplayName("거리로 구성된 세트의 총 거리를 게산한다.")
    @Test
    void calculateTotalDistance_withDistanceUnits() {
        // given
        WorkoutSet s1 = new WorkoutSet(1, WorkoutType.E, WorkoutSet.UnitType.DISTANCE, 400);
        WorkoutSet s2 = new WorkoutSet(2, WorkoutType.E, WorkoutSet.UnitType.DISTANCE, 600);
        WorkoutTemplate template = new WorkoutTemplate("t1", WorkoutType.E, List.of(s1, s2));

        Map<RunningType, Double> paces = new HashMap<>();
        paces.put(RunningType.E, 5.0);
        paces.put(RunningType.M, 6.0);

        // when
        double result = template.calculateTotalDistance(paces);

        // then
        assertThat(result).isEqualTo(1000);
    }

    @DisplayName("시간으로 구성된 세트의 총 거리를 계산한다.")
    @Test
    void calculateTotalDistance_withDurationUnits() {
        // given
        WorkoutSet s1 = new WorkoutSet(1, WorkoutType.T, WorkoutSet.UnitType.DURATION, 3);
        WorkoutTemplate template = new WorkoutTemplate("t2", WorkoutType.T, List.of(s1));

        Map<RunningType, Double> paces = new HashMap<>();
        paces.put(RunningType.T, 5.0);
        paces.put(RunningType.M, 6.0);

        // when
        double result = template.calculateTotalDistance(paces);

        // then
        assertThat(result).isEqualTo(600);
    }

    @DisplayName("시간, 거리로 섞여있는 세트의 총 거리를 계산한다.")
    @Test
    void calculateTotalDistance_mixedUnits() {
        // given: 500m + 300초 (5분) @ 5min/km
        // 기대값: 500m + 1000m = 1500m
        WorkoutSet s1 = new WorkoutSet(1, WorkoutType.I, WorkoutSet.UnitType.DISTANCE, 500);
        WorkoutSet s2 = new WorkoutSet(2, WorkoutType.I, WorkoutSet.UnitType.DURATION, 3);
        WorkoutTemplate template = new WorkoutTemplate("t3", WorkoutType.I, List.of(s1, s2));

        Map<RunningType, Double> paces = new HashMap<>();
        paces.put(RunningType.I, 5.0);
        paces.put(RunningType.M, 6.0);

        // when
        double result = template.calculateTotalDistance(paces);

        // then
        assertThat(result).isEqualTo(1100);
    }

}
