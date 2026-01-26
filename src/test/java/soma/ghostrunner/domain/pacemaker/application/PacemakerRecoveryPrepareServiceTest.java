package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.application.dto.RecoveryContext;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerRecoveryPrepareServiceTest {

    @Mock
    private PacemakerRepository pacemakerRepository;

    @Mock
    private PacemakerSetRepository pacemakerSetRepository;

    @Mock
    private MemberService memberService;

    private PacemakerRecoveryPrepareService service;

    @BeforeEach
    void setUp() {
        service = new PacemakerRecoveryPrepareService(
                pacemakerRepository,
                pacemakerSetRepository,
                memberService
        );
    }

    private Pacemaker createMockPacemaker(Long pacemakerId) {
        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemaker.getId()).thenReturn(pacemakerId);
        when(pacemaker.getMemberUuid()).thenReturn("member-uuid");
        when(pacemaker.getRunningType()).thenReturn(RunningType.R);
        when(pacemaker.getGoalDistance()).thenReturn(10.0);
        when(pacemaker.getExpectedTime()).thenReturn(50);
        when(pacemaker.getCondition()).thenReturn(3);
        when(pacemaker.getTemperature()).thenReturn(20);
        return pacemaker;
    }

    private void setupMemberMocks() {
        Member member = mock(Member.class);
        when(member.getUuid()).thenReturn("member-uuid");
        when(memberService.findMemberByUuid("member-uuid")).thenReturn(member);
        when(memberService.findMemberVdot("member-uuid")).thenReturn(45);
    }

    @Nested
    @DisplayName("prepareForRecovery")
    class PrepareForRecovery {

        @DisplayName("상태를 업데이트하고 RecoveryContext를 반환한다")
        @Test
        void shouldUpdateStatusAndReturnContext() {
            // given
            Long pacemakerId = 1L;
            Pacemaker pacemaker = createMockPacemaker(pacemakerId);
            when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));
            setupMemberMocks();
            when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId))
                    .thenReturn(Collections.emptyList());

            // when
            RecoveryContext context = service.prepareForRecovery(pacemakerId);

            // then
            verify(pacemaker).updateLastRetryAt();
            verify(pacemaker).proceedForRetry();
            assertThat(context.vdot()).isEqualTo(45);
            assertThat(context.pacemakerId()).isEqualTo(pacemakerId);
        }

        @DisplayName("PacemakerSet이 있으면 WorkoutDto에 포함된다")
        @Test
        void withSets_shouldIncludeInWorkoutDto() {
            // given
            Long pacemakerId = 1L;
            Pacemaker pacemaker = createMockPacemaker(pacemakerId);
            when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));
            setupMemberMocks();

            PacemakerSet pacemakerSet = mock(PacemakerSet.class);
            when(pacemakerSet.getSetNum()).thenReturn(1);
            when(pacemakerSet.getPace()).thenReturn(5.30);
            when(pacemakerSet.getStartPoint()).thenReturn(0.0);
            when(pacemakerSet.getEndPoint()).thenReturn(10.0);
            when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId))
                    .thenReturn(List.of(pacemakerSet));

            // when
            RecoveryContext context = service.prepareForRecovery(pacemakerId);

            // then
            assertThat(context.workoutDto().getSets()).hasSize(1);
        }
    }

}
