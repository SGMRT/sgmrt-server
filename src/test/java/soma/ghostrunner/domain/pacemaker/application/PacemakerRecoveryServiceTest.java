package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerRecoveryServiceTest {

    @InjectMocks
    private PacemakerRecoveryService service;

    @Mock
    private PacemakerRepository pacemakerRepository;

    @Mock
    private PacemakerSetRepository pacemakerSetRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private VdotService vdotService;

    @Mock
    private PacemakerLlmService llmService;

    @DisplayName("복구 대상 Pacemaker가 없으면 아무 작업도 하지 않는다")
    @Test
    void recoverStalePacemakers_whenNoTargets_shouldDoNothing() {
        // given
        when(pacemakerRepository.findRecoveryTargets(any(), any()))
                .thenReturn(Collections.emptyList());

        // when
        service.recoverStalePacemakers();

        // then
        verify(pacemakerRepository).findRecoveryTargets(any(), any());
        verifyNoInteractions(memberService);
        verifyNoInteractions(llmService);
    }

    @DisplayName("복구 대상 Pacemaker가 있으면 LLM 재호출을 수행한다")
    @Test
    void recoverStalePacemakers_whenTargetsExist_shouldCallLlm() {
        // given
        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemaker.getId()).thenReturn(1L);
        when(pacemaker.getStatus()).thenReturn(Pacemaker.Status.PROCEEDING);
        when(pacemaker.getMemberUuid()).thenReturn("member-uuid");
        when(pacemaker.getRunningType()).thenReturn(RunningType.R);
        when(pacemaker.getGoalDistance()).thenReturn(10.0);
        when(pacemaker.getExpectedTime()).thenReturn(50);
        when(pacemaker.getCondition()).thenReturn(3);
        when(pacemaker.getTemperature()).thenReturn(20);

        when(pacemakerRepository.findRecoveryTargets(any(), any()))
                .thenReturn(List.of(pacemaker));

        Member member = mock(Member.class);
        when(member.getUuid()).thenReturn("member-uuid");
        when(memberService.findMemberByUuid("member-uuid")).thenReturn(member);
        when(memberService.findMemberVdot("member-uuid")).thenReturn(45);

        PacemakerSet pacemakerSet = mock(PacemakerSet.class);
        when(pacemakerSet.getSetNum()).thenReturn(1);
        when(pacemakerSet.getPace()).thenReturn(5.30);
        when(pacemakerSet.getStartPoint()).thenReturn(0.0);
        when(pacemakerSet.getEndPoint()).thenReturn(10.0);
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(1L))
                .thenReturn(List.of(pacemakerSet));

        // when
        service.recoverStalePacemakers();

        // then
        verify(pacemaker).updateLastRetryAt();
        verify(pacemaker).proceedForRetry();
        verify(llmService).requestLlmToCreatePacemaker(
                eq(member),
                any(),
                eq(45),
                eq(3),
                eq(20),
                eq(1L),
                eq(null)  // 워커 재시도는 Rate Limit 카운트 안 함
        );
    }

    @DisplayName("복구 중 예외가 발생해도 다른 Pacemaker 복구를 계속한다")
    @Test
    void recoverStalePacemakers_whenExceptionOccurs_shouldContinueWithOthers() {
        // given
        Pacemaker pacemaker1 = mock(Pacemaker.class);
        when(pacemaker1.getId()).thenReturn(1L);
        when(pacemaker1.getStatus()).thenReturn(Pacemaker.Status.PROCEEDING);
        when(pacemaker1.getMemberUuid()).thenReturn("member-uuid-1");

        Pacemaker pacemaker2 = mock(Pacemaker.class);
        when(pacemaker2.getId()).thenReturn(2L);
        when(pacemaker2.getStatus()).thenReturn(Pacemaker.Status.INIT);
        when(pacemaker2.getMemberUuid()).thenReturn("member-uuid-2");
        when(pacemaker2.getRunningType()).thenReturn(RunningType.R);
        when(pacemaker2.getGoalDistance()).thenReturn(5.0);
        when(pacemaker2.getExpectedTime()).thenReturn(30);
        when(pacemaker2.getCondition()).thenReturn(3);
        when(pacemaker2.getTemperature()).thenReturn(15);

        when(pacemakerRepository.findRecoveryTargets(any(), any()))
                .thenReturn(List.of(pacemaker1, pacemaker2));

        // pacemaker1 복구 시 예외 발생
        when(memberService.findMemberByUuid("member-uuid-1"))
                .thenThrow(new RuntimeException("회원 조회 실패"));

        // pacemaker2는 정상 복구
        Member member2 = mock(Member.class);
        when(member2.getUuid()).thenReturn("member-uuid-2");
        when(memberService.findMemberByUuid("member-uuid-2")).thenReturn(member2);
        when(memberService.findMemberVdot("member-uuid-2")).thenReturn(40);
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(2L))
                .thenReturn(Collections.emptyList());

        // when
        service.recoverStalePacemakers();

        // then - pacemaker2는 정상적으로 LLM 호출
        verify(llmService).requestLlmToCreatePacemaker(
                eq(member2),
                any(),
                eq(40),
                eq(3),
                eq(15),
                eq(2L),
                eq(null)
        );
    }

    @DisplayName("INIT 상태의 Pacemaker는 PROCEEDING으로 전이한다")
    @Test
    void recoverStalePacemakers_whenInitStatus_shouldProceed() {
        // given
        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemaker.getId()).thenReturn(1L);
        when(pacemaker.getStatus()).thenReturn(Pacemaker.Status.INIT);
        when(pacemaker.getMemberUuid()).thenReturn("member-uuid");
        when(pacemaker.getRunningType()).thenReturn(RunningType.R);
        when(pacemaker.getGoalDistance()).thenReturn(10.0);
        when(pacemaker.getExpectedTime()).thenReturn(50);
        when(pacemaker.getCondition()).thenReturn(3);
        when(pacemaker.getTemperature()).thenReturn(20);

        when(pacemakerRepository.findRecoveryTargets(any(), any()))
                .thenReturn(List.of(pacemaker));

        Member member = mock(Member.class);
        when(member.getUuid()).thenReturn("member-uuid");
        when(memberService.findMemberByUuid("member-uuid")).thenReturn(member);
        when(memberService.findMemberVdot("member-uuid")).thenReturn(45);
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(1L))
                .thenReturn(Collections.emptyList());

        // when
        service.recoverStalePacemakers();

        // then
        verify(pacemaker).proceedForRetry();
    }

}
