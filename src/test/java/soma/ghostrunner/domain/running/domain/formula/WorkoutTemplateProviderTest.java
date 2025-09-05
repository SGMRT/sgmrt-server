package soma.ghostrunner.domain.running.domain.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import soma.ghostrunner.IntegrationTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class WorkoutTemplateProviderTest extends IntegrationTestSupport {

    @Autowired
    private WorkoutTemplateProvider workoutTemplateProvider;

    @DisplayName("M 러닝 유형의 훈련표를 조회한다.")
    @Test
    void findMWorkoutTemplate() {
        // when
        List<WorkoutTemplate> mWorkoutTemplates = workoutTemplateProvider.findWorkoutTemplates(WorkoutType.M);

        // then
        WorkoutTemplate mWorkoutTemplate = mWorkoutTemplates.get(0);
        assertThat(mWorkoutTemplate.getId()).isEqualTo("M-01");

        List<WorkoutSet> workoutSets = mWorkoutTemplate.getSets();
        assertThat(workoutSets).hasSize(3);

        WorkoutSet firstWorkoutSet = workoutSets.get(0);
        assertThat(firstWorkoutSet.setNum).isEqualTo(1);
        assertThat(firstWorkoutSet.type).isEqualTo(WorkoutType.E);
        assertThat(firstWorkoutSet.unit).isEqualTo(WorkoutSet.UnitType.DURATION);
        assertThat(firstWorkoutSet.value).isEqualTo(15);

        WorkoutSet secondWorkoutSet = workoutSets.get(1);
        assertThat(secondWorkoutSet.setNum).isEqualTo(2);
        assertThat(secondWorkoutSet.type).isEqualTo(WorkoutType.M);
        assertThat(secondWorkoutSet.unit).isEqualTo(WorkoutSet.UnitType.DURATION);
        assertThat(secondWorkoutSet.value).isEqualTo(50);
    }

}
