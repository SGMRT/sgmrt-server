package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunningTypeTest {

    @DisplayName("목적에 따라 러닝 타입으로 변환한다.")
    @Test
    void convertToRunningType() {
        // when // then
        Assertions.assertThat(RunningType.convertToRunningType("RECOVERY_JOGGING"))
                .isEqualTo(RunningType.E);

        Assertions.assertThat(RunningType.convertToRunningType("MARATHON"))
                .isEqualTo(RunningType.M);
    }

    @DisplayName("올바른 러닝 목적이 아니라면 예외를 발생한다.")
    @Test
    void convertToRunningTypeWithInvalidRunningPurpose() {
        // when // then
        Assertions.assertThatThrownBy(() -> RunningType.convertToRunningType("INVALID_PURPOSE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown running purpose: " + "INVALID_PURPOSE");
    }

}
