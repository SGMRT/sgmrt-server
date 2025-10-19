package soma.ghostrunner.domain.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.running.application.RunningVdotService;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;

import java.util.Optional;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MemberVdotUpdateEventListenerTest {

    @Mock
    private MemberService memberService;
    @Mock
    private RunningVdotService runningVdotService;
    @Mock
    private MemberVdotRepository memberVdotRepository;
    @Mock
    private MemberMapper mapper;

    @InjectMocks
    private MemberVdotUpdateEventListener runFinishedEventListener;

    @DisplayName("VDOT가 기존에 없다면 새롭게 VDOT가 저장된다.")
    @Test
    void handleRunFinishedAndSaveNewVdot() {
        // given
        String memberUuid = "18923u1uhfaiu";
        RunFinishedEvent event = new RunFinishedEvent(1L, memberUuid, 6.0, 2L);

        Member mockMember = mock(Member.class);

        given(memberService.findMemberByUuid(memberUuid)).willReturn(mockMember);
        given(runningVdotService.calculateVdot(6.0)).willReturn(50);

        given(memberVdotRepository.findByMemberId(mockMember.getId())).willReturn(Optional.empty());

        MemberVdot mapped = mock(MemberVdot.class);
        given(mapper.toMemberVdot(mockMember, 50)).willReturn(mapped);

        // when
        runFinishedEventListener.handleRunFinished(event);

        // then
        verify(memberVdotRepository, times(1)).save(mapped);
    }

    @DisplayName("VDOT가 이미 있다면 새로운 VDOT로 업데이트된다.")
    @Test
    void handleRunFinishedAndUpdateNewVdot() {
        // given
        String memberUuid = "18923u1uhfaiu";
        RunFinishedEvent event = new RunFinishedEvent(1L, memberUuid, 6.0, 2L);

        Member mockMember = mock(Member.class);
        MemberVdot mockMemberVdot = mock(MemberVdot.class);

        given(memberService.findMemberByUuid(memberUuid)).willReturn(mockMember);
        given(runningVdotService.calculateVdot(6.0)).willReturn(50);
        given(memberVdotRepository.findByMemberId(mockMember.getId())).willReturn(Optional.of(mockMemberVdot));

        // when
        runFinishedEventListener.handleRunFinished(event);

        // then
        verify(mockMemberVdot, times(1)).updateVdot(50);
    }

}
