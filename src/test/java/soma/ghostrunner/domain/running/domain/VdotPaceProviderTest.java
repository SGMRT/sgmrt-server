package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.running.infra.dto.VdotPaceDto;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

@SpringBootTest
class VdotPaceProviderTest extends IntegrationTestSupport {

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

    @DisplayName("VDOT 41인 사용자의 러닝 유형별 권장 페이스 리스트를 조회한다.")
    @Test
    void getVdotPaceByVdot() {
        // when
        List<VdotPaceDto> vdotPaces = vdotPaceProvider.getVdotPaceByVdot(41);

        // then
        Assertions.assertThat(vdotPaces)
                .hasSize(5)
                .extracting("type", "pacePerKm")
                .containsExactlyInAnyOrder(
                        tuple(RunningType.E, 6.1),
                        tuple(RunningType.M, 5.22),
                        tuple(RunningType.T, 5.00),
                        tuple(RunningType.I, 4.36),
                        tuple(RunningType.R, 4.15)
                );
    }

}
