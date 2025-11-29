package soma.ghostrunner.domain.running.domain.formula;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;

class RunningTipsProviderTest extends IntegrationTestSupport {

    @Autowired
    private RunningTipsProvider runningTipsProvider;

    @DisplayName("러닝 팁이 랜덤으로 조회되는지 검사한다.")
    @Test
    void getRandomTip() {
        // given // when
        String tip = runningTipsProvider.getRandomTip();

        // then
        System.out.println(tip);
        Assertions.assertThat(tip).isNotEmpty();
    }

}
