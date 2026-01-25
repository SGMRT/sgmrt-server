package soma.ghostrunner.domain.pacemaker.application.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.pacemaker.application.PacemakerRecoveryService;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerRecoveryWorkerTest {

    @InjectMocks
    private PacemakerRecoveryWorker worker;

    @Mock
    private PacemakerRecoveryService recoveryService;

    @DisplayName("워커 실행 시 recoveryService.recoverStalePacemakers()를 호출한다")
    @Test
    void recoverStalePacemakers_shouldCallRecoveryService() {
        // when
        worker.recoverStalePacemakers();

        // then
        verify(recoveryService).recoverStalePacemakers();
    }

    @DisplayName("recoveryService에서 예외가 발생해도 워커는 예외를 던지지 않는다")
    @Test
    void recoverStalePacemakers_whenExceptionOccurs_shouldNotThrow() {
        // given
        doThrow(new RuntimeException("복구 서비스 오류"))
                .when(recoveryService).recoverStalePacemakers();

        // when & then
        assertThatNoException()
                .isThrownBy(() -> worker.recoverStalePacemakers());
    }

}
