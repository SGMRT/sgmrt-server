package soma.ghostrunner.domain.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.pacemaker.application.VdotService;

import java.util.Optional;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MemberVdotWriterTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private VdotService vdotService;
    @Mock
    private MemberVdotRepository memberVdotRepository;
    @Mock
    private MemberMapper mapper;

    @InjectMocks
    private MemberVdotWriter memberVdotWriter;

    @DisplayName("VDOT가 기존에 없다면 새롭게 VDOT가 저장된다.")
    @Test
    void handleRunFinishedAndSaveNewVdot() {
        // given
        String memberUuid = "18923u1uhfaiu";

        Member mockMember = mock(Member.class);

        given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(mockMember));
        given(vdotService.calculateVdot(6.0)).willReturn(50);

        given(memberVdotRepository.findByMemberUuid(mockMember.getUuid())).willReturn(Optional.empty());

        MemberVdot mapped = mock(MemberVdot.class);
        given(mapper.toMemberVdot(mockMember, 50)).willReturn(mapped);

        // when
        memberVdotWriter.updateFromRun(memberUuid, 6.0);

        // then
        verify(memberVdotRepository, times(1)).save(mapped);
    }

    @DisplayName("VDOT가 이미 있다면 새로운 VDOT로 업데이트된다.")
    @Test
    void handleRunFinishedAndUpdateNewVdot() {
        // given
        String memberUuid = "18923u1uhfaiu";

        Member mockMember = mock(Member.class);
        MemberVdot mockMemberVdot = mock(MemberVdot.class);

        given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(mockMember));
        given(vdotService.calculateVdot(6.0)).willReturn(50);
        given(memberVdotRepository.findByMemberUuid(mockMember.getUuid())).willReturn(Optional.of(mockMemberVdot));

        // when
        memberVdotWriter.updateFromRun(memberUuid, 6.0);

        // then
        verify(mockMemberVdot, times(1)).updateVdot(50);
    }


    @DisplayName("레벨 기반 초기 설정: VDOT가 없으면 계산해서 저장한다.")
    @Test
    void initializeFromRunningLevel_savesWhenAbsent() {
        // given
        String memberUuid = "18923u1uhfaiu";
        Member mockMember = mock(Member.class);
        given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(mockMember));
        given(memberVdotRepository.existsByMemberUuid(memberUuid)).willReturn(false);
        given(vdotService.calculateVdotFromRunningLevel("입문자")).willReturn(38);
        MemberVdot mapped = mock(MemberVdot.class);
        given(mapper.toMemberVdot(mockMember, 38)).willReturn(mapped);

        // when
        memberVdotWriter.initializeFromRunningLevel(memberUuid, "입문자");

        // then
        verify(memberVdotRepository, times(1)).save(mapped);
    }

    @DisplayName("레벨 기반 초기 설정: 이미 VDOT가 있으면 예외를 던진다.")
    @Test
    void initializeFromRunningLevel_rejectsWhenAlreadyExists() {
        // given
        String memberUuid = "18923u1uhfaiu";
        Member mockMember = mock(Member.class);
        given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(mockMember));
        given(memberVdotRepository.existsByMemberUuid(memberUuid)).willReturn(true);

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> memberVdotWriter.initializeFromRunningLevel(memberUuid, "입문자"))
                .isInstanceOf(InvalidMemberException.class);
        verify(memberVdotRepository, never()).save(any());
    }
}
