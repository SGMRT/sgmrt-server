package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerUpdateServiceTest {

    @Mock
    PacemakerRepository pacemakerRepository;
    @Mock
    PacemakerQueryService queryService;

    PacemakerUpdateService updateService;

    @BeforeEach
    void setUp() {
        updateService = new PacemakerUpdateService(pacemakerRepository, queryService);
    }

    @Test
    @DisplayName("러닝 완료 후 페이스메이커 상태를 업데이트한다")
    void updateAfterRunning_success() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;
        Long runningId = 200L;

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, memberUuid);
        pacemaker.proceed();
        pacemaker.complete("요약", 10.0, 50, "메시지");

        when(queryService.findPacemaker(pacemakerId)).thenReturn(pacemaker);

        // when
        updateService.updateAfterRunning(memberUuid, pacemakerId, runningId);

        // then
        assertThat(pacemaker.getHasRunWith()).isTrue();
        assertThat(pacemaker.getRunningId()).isEqualTo(runningId);
    }

    @Test
    @DisplayName("러닝 업데이트 시 본인 소유가 아니면 AccessDeniedException을 던진다")
    void updateAfterRunning_notMine_throwsException() {
        // given
        String owner = "owner-uuid";
        String other = "other-uuid";
        Long pacemakerId = 100L;

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, owner);
        when(queryService.findPacemaker(pacemakerId)).thenReturn(pacemaker);

        // when/then
        assertThatThrownBy(() -> updateService.updateAfterRunning(other, pacemakerId, 200L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("페이스메이커를 삭제한다 (소프트 삭제)")
    void deletePacemaker_success() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, memberUuid);
        when(queryService.findPacemaker(pacemakerId)).thenReturn(pacemaker);

        // when
        updateService.deletePacemaker(memberUuid, pacemakerId);

        // then
        verify(pacemakerRepository).softDelete(pacemakerId);
        verify(pacemakerRepository).softDeleteAllByPacemakerId(pacemakerId);
    }

    @Test
    @DisplayName("삭제 시 본인 소유가 아니면 AccessDeniedException을 던진다")
    void deletePacemaker_notMine_throwsException() {
        // given
        String owner = "owner-uuid";
        String other = "other-uuid";
        Long pacemakerId = 100L;

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, owner);
        when(queryService.findPacemaker(pacemakerId)).thenReturn(pacemaker);

        // when/then
        assertThatThrownBy(() -> updateService.deletePacemaker(other, pacemakerId))
                .isInstanceOf(AccessDeniedException.class);

        verify(pacemakerRepository, never()).softDelete(anyLong());
        verify(pacemakerRepository, never()).softDeleteAllByPacemakerId(anyLong());
    }

}
