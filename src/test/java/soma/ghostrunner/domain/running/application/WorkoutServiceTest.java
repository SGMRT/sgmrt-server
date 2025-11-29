package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class WorkoutServiceTest extends IntegrationTestSupport {

    @Autowired
    private WorkoutService workoutService;

    @DisplayName("VDOT : 35, 목표거리 : 12K, 마라톤(M)을 목적으로 뛰고싶은 사람의 훈련표를 생성한다.")
    @Test
    void generateWorkouts_Vdot35_TargetDistance12_Marathon() {
        // given
        Map<RunningType, Double> expectedPaces = new HashMap<>();
        expectedPaces.put(RunningType.E, 7.0);
        expectedPaces.put(RunningType.M, 6.5);
        expectedPaces.put(RunningType.T, 6.8);
        expectedPaces.put(RunningType.I, 6.0);
        expectedPaces.put(RunningType.R, 6.2);

        // when
        WorkoutDto processedWorkouts = workoutService.generateWorkouts(12, RunningType.M, expectedPaces);

        // then
        List<WorkoutSetDto> workoutSetDtos = processedWorkouts.getSets();
        Assertions.assertThat(workoutSetDtos.get(workoutSetDtos.size()-1).getEndPoint()).isEqualTo(12.00);
        for (WorkoutSetDto processedWorkout : workoutSetDtos) {
            System.out.println(processedWorkout);
        }
    }

}
