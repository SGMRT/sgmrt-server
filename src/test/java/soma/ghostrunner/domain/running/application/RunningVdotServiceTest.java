package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.Map;

@SpringBootTest
class RunningVdotServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningVdotService runningVdotService;

    @DisplayName("VDOT 41인 사용자의 러닝 유형별 권장 페이스 리스트를 조회한다.")
    @Test
    void getVdotPaceByVdot() {
        // when
        Map<RunningType, Double> vdotPaces = runningVdotService.getExpectedPacesByVdot(41);

        // then
        Assertions.assertThat(vdotPaces.get(RunningType.E)).isEqualTo(6.1);
        Assertions.assertThat(vdotPaces.get(RunningType.M)).isEqualTo(5.22);
        Assertions.assertThat(vdotPaces.get(RunningType.T)).isEqualTo(5.00);
        Assertions.assertThat(vdotPaces.get(RunningType.I)).isEqualTo(4.36);
        Assertions.assertThat(vdotPaces.get(RunningType.R)).isEqualTo(4.15);
    }

}
