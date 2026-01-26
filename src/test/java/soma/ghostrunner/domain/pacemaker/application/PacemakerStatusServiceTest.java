package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacemakerStatusServiceTest {

    @InjectMocks
    PacemakerStatusService statusService;

    @Mock
    PacemakerRepository pacemakerRepository;

    @Nested
    @DisplayName("updateToProceeding")
    class UpdateToProceeding {

        @DisplayName("INIT 상태의 Pacemaker를 PROCEEDING으로 업데이트한다")
        @Test
        void success() {
            // given
            Long pacemakerId = 100L;
            Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, "member-uuid");

            when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

            // when
            statusService.updateToProceeding(pacemakerId);

            // then
            assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.PROCEEDING);
        }
    }

    @Nested
    @DisplayName("updateToFallback")
    class UpdateToFallback {

        @DisplayName("INIT 상태의 Pacemaker를 FALLBACK으로 업데이트한다")
        @Test
        void fromInit_success() {
            // given
            Long pacemakerId = 100L;
            Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, "member-uuid");
            // 현재 INIT 상태

            when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

            // when
            statusService.updateToFallback(pacemakerId);

            // then
            assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.FALLBACK);
        }

        @DisplayName("PROCEEDING 상태의 Pacemaker를 FALLBACK으로 업데이트한다")
        @Test
        void fromProceeding_success() {
            // given
            Long pacemakerId = 100L;
            Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, "member-uuid");
            pacemaker.proceed();  // INIT -> PROCEEDING

            when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

            // when
            statusService.updateToFallback(pacemakerId);

            // then
            assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.FALLBACK);
        }
    }

}
