package soma.ghostrunner.domain.running.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class VdotPaceProviderTest {

    @Autowired
    private VdotPaceProvider vdotPaceProvider;

    @DisplayName("VDOT 20인 사용자가 T 러닝으로 뛰고 싶다면 8.41 페이스가 나온다.")
    @Test
    void getPaceByVdotAndRunningTypeWithRunningTypeTAndVdot20() {
        // when // then
        assertThat(vdotPaceProvider.getPaceByVdotAndRunningType(20, RunningType.T)).isEqualTo(8.41);
    }

    @DisplayName("VDOT 41인 사용자가 I 러닝으로 뛰고 싶다면 4.36 페이스가 나온다.")
    @Test
    void getPaceByVdotAndRunningTypeWithRunningTypeIAndVdot41() {
        // when // then
        assertThat(vdotPaceProvider.getPaceByVdotAndRunningType(41, RunningType.I)).isEqualTo(4.36);
    }

}
