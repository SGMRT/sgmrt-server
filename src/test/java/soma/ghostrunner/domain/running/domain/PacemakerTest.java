package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PacemakerTest {

    @DisplayName("LLM API 통신 상태를 업데이트한다.")
    @Test
    void updateStatus() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "멤버 UUID");

        // when
        pacemaker.updateStatus(Pacemaker.Status.COMPLETED);

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.COMPLETED);
    }

    @DisplayName("페이스메이커의 주인인지 검증한다.")
    @Test
    void verifyMember() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "이복둥의 UUID");

        // when // then
        Assertions.assertThatThrownBy(() -> pacemaker.verifyMember("이진의 UUID"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");
    }

}
