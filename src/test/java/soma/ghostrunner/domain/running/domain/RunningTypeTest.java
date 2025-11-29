package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunningTypeTest {

    @DisplayName("목적에 따라 러닝 타입으로 변환한다.")
    @Test
    void toRunningType() {
        // when // then
        Assertions.assertThat(RunningType.toRunningType("RECOVERY_JOGGING"))
                .isEqualTo(RunningType.E);

        Assertions.assertThat(RunningType.toRunningType("MARATHON"))
                .isEqualTo(RunningType.M);
    }

    @DisplayName("올바른 러닝 목적이 아니라면 예외를 발생한다.")
    @Test
    void toRunningTypeWithInvalidRunningPurpose() {
        // when // then
        Assertions.assertThatThrownBy(() -> RunningType.toRunningType("INVALID_PURPOSE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown running purpose: " + "INVALID_PURPOSE");
    }

    @DisplayName("러닝 타입에 따라 운동 용어로 변환한다.")
    @Test
    void toWorkoutWord() {
        // given // when
        RunningType runningType1 = RunningType.toRunningType("RECOVERY_JOGGING");
        RunningType runningType2 = RunningType.toRunningType("MARATHON");

        // then
        Assertions.assertThat(runningType1.toWorkoutWord()).isEqualTo("RECOVERY_JOGGING");
        Assertions.assertThat(runningType2.toWorkoutWord()).isEqualTo("MARATHON");
    }

}
