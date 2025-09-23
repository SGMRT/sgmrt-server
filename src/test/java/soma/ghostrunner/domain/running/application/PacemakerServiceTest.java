package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerServiceTest {

    @Mock
    PacemakerRepository pacemakerRepository;
    @Mock
    PacemakerSetRepository pacemakerSetRepository;
    @Mock
    RedisRunningRepository redisRunningRepository;
    @Mock
    MemberService memberService;
    @Mock RunningVdotService runningVdotService;
    @Mock WorkoutService workoutService;
    @Mock PacemakerLlmService llmService;
    @Mock
    RunningApplicationMapper mapper;
    @Mock
    RLock rLock;

    PacemakerService sut;

    @BeforeEach
    void setUp() {
        sut = new PacemakerService(
                pacemakerRepository,
                pacemakerSetRepository,
                redisRunningRepository,
                memberService,
                runningVdotService,
                workoutService,
                llmService,
                mapper
        );
    }

    @Test
    void 성공_흐름_오케스트레이션() throws Exception {
        // given
        String uuid = "member-123";
        Member member = Member.of("이복둥", "url");
        CreatePacemakerCommand cmd = new CreatePacemakerCommand("MARATHON", 10.0, 5, 30, 6.2, LocalDate.now());

        when(memberService.findMemberByUuid(uuid)).thenReturn(member);
        when(runningVdotService.calculateVdot(anyDouble())).thenReturn(30);
        when(runningVdotService.getExpectedPacesByVdot(30)).thenReturn(Map.of(RunningType.M, 6.2));
        when(workoutService.generateWorkouts(anyDouble(), any(), any())).thenReturn(WorkoutDto.of(RunningType.M, 10.0, List.of()));
        when(redisRunningRepository.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(redisRunningRepository.incrementRateLimitCounter(any(), anyLong(), anyInt())).thenReturn(1L);

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, uuid);
        when(mapper.toPacemaker(any(), eq(cmd), eq(member))).thenReturn(pacemaker);
        when(pacemakerRepository.save(any())).thenReturn(pacemaker);

        // when
        Long id = sut.createPaceMaker(uuid, cmd);

        // then
        assertThat(id).isEqualTo(pacemaker.getId());
        InOrder inOrder = inOrder(memberService, runningVdotService, workoutService,
                pacemakerRepository, llmService);
        inOrder.verify(memberService).findMemberByUuid(uuid);
        inOrder.verify(runningVdotService).calculateVdot(anyDouble());
        inOrder.verify(runningVdotService).getExpectedPacesByVdot(30);
        inOrder.verify(workoutService).generateWorkouts(anyDouble(), any(), any());
        inOrder.verify(pacemakerRepository).save(any());
        inOrder.verify(llmService).requestLlmToCreatePacemaker(
                eq(member), any(), eq(30), anyInt(), anyInt(), eq(pacemaker.getId()), anyString()
        );
    }

    @Test
    void 목표거리가_3km미만이면_예외발생() {
        // given
        String uuid = "member-123";
        CreatePacemakerCommand cmd = new CreatePacemakerCommand("MARATHON", 2.0, 5, 30, 6.2, LocalDate.now());

        // when // then
        assertThatThrownBy(() -> sut.createPaceMaker(uuid, cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("3km 미만의 거리는 페이스메이커를 생성할 수 없습니다.");

        verifyNoInteractions(memberService, pacemakerRepository, llmService);
    }

    @Test
    void 일일제한_초과시_예외발생() throws Exception {
        // given
        String uuid = "member-123";
        Member member = Member.of("이복둥", "url");
        CreatePacemakerCommand cmd = new CreatePacemakerCommand("MARATHON", 10.0, 5, 30, 6.2, LocalDate.now());

        when(memberService.findMemberByUuid(uuid)).thenReturn(member);
        when(runningVdotService.calculateVdot(anyDouble())).thenReturn(30);
        when(runningVdotService.getExpectedPacesByVdot(anyInt())).thenReturn(Map.of());
        when(workoutService.generateWorkouts(anyDouble(), any(), any())).thenReturn(WorkoutDto.of(RunningType.M, 10.0, List.of()));
        when(redisRunningRepository.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(redisRunningRepository.incrementRateLimitCounter(any(), anyLong(), anyInt()))
                .thenReturn(4L); // 3회 초과

        // when // then
        assertThatThrownBy(() -> sut.createPaceMaker(uuid, cmd))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("일일 사용량을 초과했습니다.");

        verifyNoInteractions(pacemakerRepository, llmService);
    }

    @Test
    @DisplayName("완료된 페이스메이커는 세트를 조회하고 mapper.toResponse(pacemaker, sets)로 응답한다")
    void getPacemaker_completed_success() {
        // given
        String owner = "owner-uuid";
        Long id = 100L;

        Pacemaker completed = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, owner);
        // 도메인에서 완료 상태로 전이
        completed.updateSucceedPacemaker("요약", 10.0, 50, "메세지");

        List<PacemakerSet> sets = List.of(
                PacemakerSet.of(1, "메세지1", null, null, null, null),
                PacemakerSet.of(2, "메세지1", null, null, null, null)
        );
        PacemakerPollingResponse expected = new PacemakerPollingResponse(); // 실제 타입에 맞게
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(completed));
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(id)).thenReturn(sets);
        when(mapper.toResponse(completed, sets)).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = sut.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);

        InOrder inOrder = inOrder(pacemakerRepository, pacemakerSetRepository, mapper);
        inOrder.verify(pacemakerRepository).findById(id);
        inOrder.verify(pacemakerSetRepository).findByPacemakerIdOrderBySetNumAsc(id);
        inOrder.verify(mapper).toResponse(completed, sets);

        // 진행상태용 mapper는 호출되지 않아야 함
        verify(mapper, never()).toResponse(Pacemaker.Status.COMPLETED);
        verifyNoMoreInteractions(pacemakerRepository, pacemakerSetRepository, mapper);
        verifyNoInteractions(llmService, runningVdotService, workoutService, memberService, redisRunningRepository);
    }

    @Test
    @DisplayName("진행/실패 상태면 세트 조회 없이 mapper.toResponse(status)로 응답한다")
    void getPacemaker_processing_returnsStatusOnly() {
        // given
        String owner = "owner-uuid";
        Long id = 101L;

        Pacemaker processing = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, owner);
        // 기본 상태가 진행중이라 가정(또는 명시적으로 실패/진행 상태로 전이하는 메서드 호출)

        // 도메인이 돌려줄 status (예: "PROCEEDING" 또는 "FAILED")
        Pacemaker.Status status = Pacemaker.Status.PROCEEDING;

        PacemakerPollingResponse expected = new PacemakerPollingResponse();
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(processing));
        when(mapper.toResponse(status)).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = sut.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);

        // 세트 조회/완료용 mapper 호출은 없어야 함
        verify(pacemakerSetRepository, never()).findByPacemakerIdOrderBySetNumAsc(anyLong());
        verify(mapper, never()).toResponse(eq(processing), anyList());

        InOrder inOrder = inOrder(pacemakerRepository, mapper);
        inOrder.verify(pacemakerRepository).findById(id);
        inOrder.verify(mapper).toResponse(status);

        verifyNoMoreInteractions(pacemakerRepository, mapper);
        verifyNoInteractions(llmService, runningVdotService, workoutService, memberService, redisRunningRepository);
    }

    @Test
    @DisplayName("본인 소유가 아니면 AccessDeniedException을 던진다")
    void getPacemaker_notMine_throwsAccessDenied() {
        // given
        String owner = "owner-uuid";
        String other = "other-uuid";
        Long id = 102L;

        Pacemaker entity = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, owner);
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(entity));

        // when/then
        assertThatThrownBy(() -> sut.getPacemaker(id, other))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");

        // 세트조회/매퍼 호출 없어야 함
        verify(pacemakerSetRepository, never()).findByPacemakerIdOrderBySetNumAsc(anyLong());
        verify(mapper, never()).toResponse(Pacemaker.Status.FAILED);
        verify(mapper, never()).toResponse(any(Pacemaker.class), anyList());
    }

    @Test
    @DisplayName("Pacemaker ID가 없으면 RunningNotFoundException을 던진다")
    void getPacemaker_notFound_throws() {
        // given
        Long id = 999L;
        when(pacemakerRepository.findById(id)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> sut.getPacemaker(id, "any-uuid"))
                .isInstanceOf(RunningNotFoundException.class);

        verifyNoInteractions(pacemakerSetRepository, mapper);
    }

}
