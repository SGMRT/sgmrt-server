package soma.ghostrunner.domain.running.domain.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutSetTest {

    private Map<RunningType, Double> samplePaces() {
        // 분/키로 (min/km) 가정
        Map<RunningType, Double> paces = new EnumMap<>(RunningType.class);
        paces.put(RunningType.E, 6.0);
        paces.put(RunningType.M, 5.0);
        paces.put(RunningType.T, 4.3);
        paces.put(RunningType.I, 4.0);
        paces.put(RunningType.R, 3.5);
        return paces;
    }

    @Test
    @DisplayName("unit=DISTANCE면 value를 그대로(미터) 반환한다")
    void convertToDistance_returnsRawValue_whenUnitIsDistance() {
        WorkoutSet set = new WorkoutSet(1, WorkoutType.E, WorkoutSet.UnitType.DISTANCE, 3000.0);
        double meters = set.convertToDistance(samplePaces());
        assertEquals(3000.0, meters, 1e-9);
    }

    @Test
    @DisplayName("unit=DISTANCE이고 type=X여도 그대로 반환한다 (DISTANCE 우선)")
    void convertToDistance_distanceWinsEvenIfRest() {
        WorkoutSet set = new WorkoutSet(1, WorkoutType.X, WorkoutSet.UnitType.DISTANCE, 2000.0);
        double meters = set.convertToDistance(samplePaces());
        assertEquals(2000.0, meters, 1e-9);
    }

    @Test
    @DisplayName("unit=DURATION이고 type=X(휴식)면 0m를 반환한다")
    void convertToDistance_returnsZero_whenDurationAndRest() {
        WorkoutSet set = new WorkoutSet(1, WorkoutType.X, WorkoutSet.UnitType.DURATION, 10.0); // 10분 휴식
        double meters = set.convertToDistance(samplePaces());
        assertEquals(0.0, meters, 1e-9);
    }

    @Test
    @DisplayName("unit=DURATION이면 (시간/페이스)*1000 으로 환산한다 (예: 30분, E=6.0분/키로 → 5km)")
    void convertToDistance_convertsDurationUsingPace() {
        WorkoutSet set = new WorkoutSet(1, WorkoutType.E, WorkoutSet.UnitType.DURATION, 30.0); // 30분
        double meters = set.convertToDistance(samplePaces());
        // (30분 / 6.0분/키로) = 5km -> 5000m
        assertEquals(5000.0, meters, 1e-9);
    }

    @Test
    @DisplayName("해당 러닝 타입의 pace가 없으면 NPE가 발생한다 (현 구현 기준)")
    void convertToDistance_throws_whenPaceMissing() {
        Map<RunningType, Double> paces = new EnumMap<>(RunningType.class);
        paces.put(RunningType.E, 6.0); // T pace故의로 누락

        WorkoutSet set = new WorkoutSet(1, WorkoutType.T, WorkoutSet.UnitType.DURATION, 20.0);
        assertThrows(NullPointerException.class, () -> set.convertToDistance(paces));
    }

}
