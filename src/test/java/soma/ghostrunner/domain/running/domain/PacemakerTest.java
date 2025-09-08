package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PacemakerTest {

    @DisplayName("LLM API 통신 상태를 업데이트한다.")
    @Test
    void updateStatus() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0);

        // when
        pacemaker.updateStatus(Pacemaker.Status.COMPLETED);

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.COMPLETED);
    }

}
