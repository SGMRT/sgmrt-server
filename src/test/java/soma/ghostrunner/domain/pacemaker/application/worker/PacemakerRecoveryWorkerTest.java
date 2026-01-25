package soma.ghostrunner.domain.pacemaker.application.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.pacemaker.application.PacemakerRecoveryService;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerRecoveryWorkerTest {

    @InjectMocks
    private PacemakerRecoveryWorker worker;

    @Mock
    private PacemakerRecoveryService recoveryService;

    @DisplayName("복구 대상이 있으면 각각에 대해 recoverSingle을 호출한다")
    @Test
    void recoverStalePacemakers_whenTargetsExist_shouldCallRecoverSingleForEach() {
        // given
        when(recoveryService.findRecoveryTargetIds()).thenReturn(List.of(1L, 2L, 3L));

        // when
        worker.recoverStalePacemakers();

        // then
        verify(recoveryService).findRecoveryTargetIds();
        verify(recoveryService).recoverSingle(1L);
        verify(recoveryService).recoverSingle(2L);
        verify(recoveryService).recoverSingle(3L);
    }

    @DisplayName("복구 대상이 없으면 recoverSingle을 호출하지 않는다")
    @Test
    void recoverStalePacemakers_whenNoTargets_shouldNotCallRecoverSingle() {
        // given
        when(recoveryService.findRecoveryTargetIds()).thenReturn(Collections.emptyList());

        // when
        worker.recoverStalePacemakers();

        // then
        verify(recoveryService).findRecoveryTargetIds();
        verify(recoveryService, never()).recoverSingle(anyLong());
    }

    @DisplayName("하나의 복구가 실패해도 나머지 복구는 계속 진행한다")
    @Test
    void recoverStalePacemakers_whenOneFailsOthersContinue() {
        // given
        when(recoveryService.findRecoveryTargetIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("복구 실패")).when(recoveryService).recoverSingle(2L);

        // when
        worker.recoverStalePacemakers();

        // then
        verify(recoveryService).recoverSingle(1L);
        verify(recoveryService).recoverSingle(2L);  // 실패해도 호출됨
        verify(recoveryService).recoverSingle(3L);  // 계속 진행
    }

    @DisplayName("조회 중 예외가 발생해도 워커는 예외를 던지지 않는다")
    @Test
    void recoverStalePacemakers_whenQueryFails_shouldNotThrow() {
        // given
        when(recoveryService.findRecoveryTargetIds())
                .thenThrow(new RuntimeException("조회 오류"));

        // when & then
        assertThatNoException()
                .isThrownBy(() -> worker.recoverStalePacemakers());
    }

}
