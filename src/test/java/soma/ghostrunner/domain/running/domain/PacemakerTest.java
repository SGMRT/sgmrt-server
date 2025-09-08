package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.error.exception.BusinessException;

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

    @DisplayName("아직 가공중이라면 4xx 예외를 발생한다.")
    @Test
    void verifyStatusProceeding() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "이복둥의 UUID");

        // when // then
        Assertions.assertThatThrownBy(pacemaker::verifyStatusProceedingOrFailed)
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("페이스메이커가 아직 생성되고 있습니다.");
     }

    @DisplayName("가공에 실패했다면 5xx 예외를 발생한다.")
    @Test
    void verifyStatusFailed() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "이복둥의 UUID");
        pacemaker.updateStatus(Pacemaker.Status.FAILED);

        // when // then
        Assertions.assertThatThrownBy(pacemaker::verifyStatusProceedingOrFailed)
                .isInstanceOf(BusinessException.class)
                .hasMessage("페이스메이커를 생성하는데 실패했습니다.");
    }

}
