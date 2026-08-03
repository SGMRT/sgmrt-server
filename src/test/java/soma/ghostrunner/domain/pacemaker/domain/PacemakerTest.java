package soma.ghostrunner.domain.pacemaker.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PacemakerTest {

    @DisplayName("페이스메이커 생성 시 INIT 상태로 시작한다.")
    @Test
    void createWithInitStatus() {
        // given & when
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.INIT);
    }

    @DisplayName("INIT -> PROCEEDING 상태 전이가 가능하다.")
    @Test
    void transitionFromInitToProceeding() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");

        // when
        pacemaker.proceed();

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.PROCEEDING);
    }

    @DisplayName("PROCEEDING -> COMPLETED 상태 전이가 가능하다.")
    @Test
    void transitionFromProceedingToCompleted() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        pacemaker.proceed();

        // when
        pacemaker.complete("요약", 10.0, 50, "초기 메시지");

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.COMPLETED);
        Assertions.assertThat(pacemaker.getSummary()).isEqualTo("요약");
        Assertions.assertThat(pacemaker.getExpectedTime()).isEqualTo(50);
        Assertions.assertThat(pacemaker.getInitialMessage()).isEqualTo("초기 메시지");
    }

    @DisplayName("PROCEEDING -> FALLBACK 상태 전이가 가능하다.")
    @Test
    void transitionFromProceedingToFallback() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        pacemaker.proceed();

        // when
        pacemaker.fallback();

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.FAILED);
    }

    @DisplayName("INIT에서 COMPLETED로 직접 전이할 수 없다.")
    @Test
    void cannotTransitionFromInitToCompleted() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");

        // when & then
        Assertions.assertThatThrownBy(() -> pacemaker.complete("요약", 10.0, 50, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from INIT to COMPLETED");
    }

    @DisplayName("COMPLETED 상태에서는 다른 상태로 전이할 수 없다.")
    @Test
    void cannotTransitionFromCompleted() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        pacemaker.proceed();
        pacemaker.complete("요약", 10.0, 50, "메시지");

        // when & then
        Assertions.assertThatThrownBy(pacemaker::fallback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from COMPLETED to FAILED");
    }

    @DisplayName("FALLBACK 상태에서는 다른 상태로 전이할 수 없다.")
    @Test
    void cannotTransitionFromFallback() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        pacemaker.proceed();
        pacemaker.fallback();

        // when & then
        Assertions.assertThatThrownBy(() -> pacemaker.complete("요약", 10.0, 50, "메시지"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from FAILED to COMPLETED");
    }

    @DisplayName("COMPLETED와 FALLBACK 상태는 완료된 상태로 간주한다.")
    @Test
    void isCompletedReturnsTrueForCompletedAndFallback() {
        // given
        Pacemaker completed = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        completed.proceed();
        completed.complete("요약", 10.0, 50, "메시지");

        Pacemaker fallback = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        fallback.proceed();
        fallback.fallback();

        // then
        Assertions.assertThat(completed.isCompleted()).isTrue();
        Assertions.assertThat(completed.isNotCompleted()).isFalse();
        Assertions.assertThat(fallback.isCompleted()).isTrue();
        Assertions.assertThat(fallback.isNotCompleted()).isFalse();
    }

    @DisplayName("INIT과 PROCEEDING 상태는 완료되지 않은 상태로 간주한다.")
    @Test
    void isNotCompletedReturnsTrueForInitAndProceeding() {
        // given
        Pacemaker init = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");

        Pacemaker proceeding = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        proceeding.proceed();

        // then
        Assertions.assertThat(init.isNotCompleted()).isTrue();
        Assertions.assertThat(init.isCompleted()).isFalse();
        Assertions.assertThat(proceeding.isNotCompleted()).isTrue();
        Assertions.assertThat(proceeding.isCompleted()).isFalse();
    }

    @DisplayName("페이스메이커의 주인인지 검증한다.")
    @Test
    void verifyMember() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "이복둥의 UUID");

        // when // then
        Assertions.assertThatThrownBy(() -> pacemaker.verifyMember("이진의 UUID"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");
    }

    @DisplayName("페이스메이커와 러닝 후 상태를 업대이트한다.")
    @Test
    void updateAfterRunning() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");

        // when
        pacemaker.updateAfterRunning(3L);

        // then
        Assertions.assertThat(pacemaker.getRunningId()).isEqualTo(3L);
        Assertions.assertThat(pacemaker.getHasRunWith()).isTrue();
    }

    @DisplayName("이미 함께 뛴 기록이 있는 페이스메이커라면 예외가 발생한다.")
    @Test
    void throwExceptionWhenRunWithAlreadyDone() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        pacemaker.updateAfterRunning(3L);

        // when // then
        Assertions.assertThatThrownBy(() -> pacemaker.updateAfterRunning(4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 함께 뛴 기록이 있는 페이스메이커입니다.");
    }

    @DisplayName("proceedForRetry는 INIT 상태에서 PROCEEDING으로 전이한다.")
    @Test
    void proceedForRetry_fromInit() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");

        // when
        pacemaker.proceedForRetry();

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.PROCEEDING);
    }

    @DisplayName("proceedForRetry는 PROCEEDING 상태에서 상태를 유지한다.")
    @Test
    void proceedForRetry_fromProceeding() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        pacemaker.proceed();

        // when
        pacemaker.proceedForRetry();

        // then
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.PROCEEDING);
    }

    @DisplayName("updateLastRetryAt은 lastRetryAt을 현재 시간으로 업데이트한다.")
    @Test
    void updateLastRetryAt() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.R, "멤버 UUID");
        Assertions.assertThat(pacemaker.getLastRetryAt()).isNull();

        // when
        pacemaker.updateLastRetryAt();

        // then
        Assertions.assertThat(pacemaker.getLastRetryAt()).isNotNull();
    }

    @DisplayName("createWithRuleBase는 condition과 temperature를 저장한다.")
    @Test
    void createWithRuleBase_withConditionAndTemperature() {
        // given & when
        Pacemaker pacemaker = Pacemaker.createWithRuleBase(
                Pacemaker.Norm.DISTANCE, 10.0, 50, 1L, RunningType.R, "멤버 UUID", 3, 20);

        // then
        Assertions.assertThat(pacemaker.getCondition()).isEqualTo(3);
        Assertions.assertThat(pacemaker.getTemperature()).isEqualTo(20);
        Assertions.assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.INIT);
    }

}
