package soma.ghostrunner.domain.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.running.domain.VdotCalculator;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class VdotCalculatorTest extends IntegrationTestSupport {

    @Autowired
    private VdotCalculator vdotCalculator;

    @DisplayName("1마일 페이스가 7.49일 때 VDOT 36을 반환한다.")
    @Test
    void calculateFromPace() {
        // when // then
        assertThat(vdotCalculator.calculateFromPace(7.49)).isEqualTo(36);
     }

    @DisplayName("1마일 페이스가 7.40일 때 7.38인 VDOT 37과 7.49인 VDOT 36 중 하위 VDOT인 36을 반환한다.")
    @Test
    void calculateFromPaceReturnLowerVdotWhenMiddlePaceInput() {
        // when // then
        assertThat(vdotCalculator.calculateFromPace(7.40)).isEqualTo(36);
    }

    @DisplayName("가장 느린 페이스인 12.55 보다 더 늦게 뛴 경우 가장 하위 VDOT 값인 20을 반환한다.")
    @Test
    void calculateFromPaceReturnLowestVdotWhenOutOfSlowestPaceInput() {
        // when // then
        assertThat(vdotCalculator.calculateFromPace(12.55)).isEqualTo(20);
    }

    @DisplayName("가장 빠른 페이스인 3:39 보다 더 빠르게 뛴 경우 가장 상위 VDOT 값인 85를 반환한다.")
    @Test
    void calculateFromPaceReturnHighestVdotWhenOutOfFastestPaceInput() {
        // when // then
        assertThat(vdotCalculator.calculateFromPace(3.39)).isEqualTo(85);
    }

}
