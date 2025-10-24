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
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.running.api.support.PacemakerType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    RunningQueryService runningQueryService;
    @Mock
    CourseService courseService;
    @Mock
    MemberService memberService;
    @Mock
    RunningVdotService runningVdotService;
    @Mock
    WorkoutService workoutService;
    @Mock
    PacemakerLlmService llmService;
    @Mock
    RunningApplicationMapper mapper;
    @Mock
    RLock rLock;

    PacemakerService pacemakerService;

    @BeforeEach
    void setUp() {
        pacemakerService = new PacemakerService(
                pacemakerRepository,
                pacemakerSetRepository,
                redisRunningRepository,
                runningQueryService,
                courseService,
                memberService,
                runningVdotService,
                workoutService,
                llmService,
                mapper
        );
    }

    @DisplayName("성공 흐름: VDOT 조회 → 페이스 계산 → 워크아웃 생성 → 저장 → LLM 호출")
    @Test
    void 성공_흐름_오케스트레이션() throws Exception {
        // given
        String uuid = "member-123";
        Member member = Member.of("이복둥", "url");
        member.setUuid(uuid);

        when(memberService.findMemberByUuid(uuid)).thenReturn(member);
        when(memberService.findMemberVdot(uuid)).thenReturn(30);
        Long courseId = 1L;
        CreatePacemakerCommand cmd =
                new CreatePacemakerCommand(PacemakerType.MARATHON, 10.0, 5, 30, courseId);

        when(memberService.findMemberByUuid(uuid)).thenReturn(member);
        when(memberService.findMemberVdot(uuid)).thenReturn(30);
        when(courseService.findCourseById(courseId)).thenReturn(mock(Course.class));

        when(runningVdotService.getExpectedPacesByVdot(30))
                .thenReturn(Map.of(RunningType.M, 6.2));
        when(workoutService.generateWorkouts(eq(10.0), any(), any()))
                .thenReturn(WorkoutDto.of(RunningType.M, 10.0, java.util.List.of()));

        when(redisRunningRepository.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(redisRunningRepository.incrementRateLimitCounter(anyString(), anyLong(), anyInt()))
                .thenReturn(1L);

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, uuid);
        when(mapper.toPacemaker(eq(Pacemaker.Norm.DISTANCE), eq(cmd), eq(courseId), eq(member)))
                .thenReturn(pacemaker);
        when(pacemakerRepository.save(any())).thenReturn(pacemaker);

        // when
        Long id = pacemakerService.createPaceMaker(uuid, cmd);

        // then
        assertThat(id).isEqualTo(pacemaker.getId());

        InOrder inOrder = inOrder(memberService, courseService, runningVdotService, workoutService,
                pacemakerRepository, llmService);
        inOrder.verify(memberService).findMemberByUuid(uuid);
        inOrder.verify(courseService).findCourseById(courseId);           // ✅ 추가 검증
        inOrder.verify(memberService).findMemberVdot(uuid);               // ✅ 변경된 호출
        inOrder.verify(runningVdotService).getExpectedPacesByVdot(30);
        inOrder.verify(workoutService).generateWorkouts(eq(10.0), any(), any());
        inOrder.verify(pacemakerRepository).save(any());
        inOrder.verify(llmService).requestLlmToCreatePacemaker(
                eq(member), any(), eq(30), anyInt(), anyInt(), eq(pacemaker.getId()), anyString()
        );
    }

    @DisplayName("일일 제한 초과 시 예외 발생(저장/LLM 미호출)")
    @Test
    void 일일제한_초과시_예외발생() throws Exception {
        // given
        String uuid = "member-123";
        Member member = Member.of("이복둥", "url");
        member.setUuid(uuid);

        Long courseId = 1L;
        CreatePacemakerCommand cmd =
                new CreatePacemakerCommand(PacemakerType.MARATHON, 10.0, 5, 30, courseId);

        when(memberService.findMemberByUuid(uuid)).thenReturn(member);
        when(memberService.findMemberVdot(uuid)).thenReturn(30);                // ✅ 변경
        when(courseService.findCourseById(courseId)).thenReturn(mock(Course.class)); // ✅ 추가
        when(runningVdotService.getExpectedPacesByVdot(anyInt())).thenReturn(Map.of());
        when(workoutService.generateWorkouts(anyDouble(), any(), any()))
                .thenReturn(WorkoutDto.of(RunningType.M, 10.0, java.util.List.of()));
        when(redisRunningRepository.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        // 3회 초과
        when(redisRunningRepository.incrementRateLimitCounter(anyString(), anyLong(), anyInt()))
                .thenReturn(4L);

        // when // then
        assertThatThrownBy(() -> pacemakerService.createPaceMaker(uuid, cmd))
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

        Pacemaker completed = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, owner);
        // 도메인에서 완료 상태로 전이
        completed.updateSucceedPacemaker("요약", 10.0, 50, "메세지");

        List<PacemakerSet> sets = List.of(
                PacemakerSet.of(1, "메세지1", null, null, null, null),
                PacemakerSet.of(2, "메세지1", null, null, null, null)
        );
        PacemakerPollingResponse expected = new PacemakerPollingResponse(); // 실제 타입에 맞게
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(completed));
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(id)).thenReturn(sets);
        when(mapper.toPacemakerPollingResponse(completed, sets)).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = pacemakerService.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);

        InOrder inOrder = inOrder(pacemakerRepository, pacemakerSetRepository, mapper);
        inOrder.verify(pacemakerRepository).findById(id);
        inOrder.verify(pacemakerSetRepository).findByPacemakerIdOrderBySetNumAsc(id);
        inOrder.verify(mapper).toPacemakerPollingResponse(completed, sets);

        // 진행상태용 mapper는 호출되지 않아야 함
        verify(mapper, never()).toPacemakerPollingResponse(completed);
        verifyNoMoreInteractions(pacemakerRepository, pacemakerSetRepository, mapper);
        verifyNoInteractions(llmService, runningVdotService, workoutService, memberService, redisRunningRepository);
    }

    @Test
    @DisplayName("진행/실패 상태면 세트 조회 없이 mapper.toResponse(status)로 응답한다")
    void getPacemaker_processing_returnsStatusOnly() {
        // given
        String owner = "owner-uuid";
        Long id = 101L;

        Pacemaker processing = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, owner);
        // 기본 상태가 진행중이라 가정(또는 명시적으로 실패/진행 상태로 전이하는 메서드 호출)

        // 도메인이 돌려줄 status (예: "PROCEEDING" 또는 "FAILED")
        Pacemaker.Status status = Pacemaker.Status.PROCEEDING;

        PacemakerPollingResponse expected = new PacemakerPollingResponse();
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(processing));
        when(mapper.toPacemakerPollingResponse(processing)).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = pacemakerService.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);

        // 세트 조회/완료용 mapper 호출은 없어야 함
        verify(pacemakerSetRepository, never()).findByPacemakerIdOrderBySetNumAsc(anyLong());
        verify(mapper, never()).toPacemakerPollingResponse(eq(processing), anyList());

        InOrder inOrder = inOrder(pacemakerRepository, mapper);
        inOrder.verify(pacemakerRepository).findById(id);
        inOrder.verify(mapper).toPacemakerPollingResponse(processing);

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

        Pacemaker entity = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, owner);
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(entity));

        // when/then
        assertThatThrownBy(() -> pacemakerService.getPacemaker(id, other))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");

        // 세트조회/매퍼 호출 없어야 함
        verify(pacemakerSetRepository, never()).findByPacemakerIdOrderBySetNumAsc(anyLong());
        verify(mapper, never()).toPacemakerPollingResponse(entity);
        verify(mapper, never()).toPacemakerPollingResponse(any(Pacemaker.class), anyList());
    }

    @Test
    @DisplayName("Pacemaker ID가 없으면 RunningNotFoundException을 던진다")
    void getPacemaker_notFound_throws() {
        // given
        Long id = 999L;
        when(pacemakerRepository.findById(id)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> pacemakerService.getPacemaker(id, "any-uuid"))
                .isInstanceOf(RunningNotFoundException.class);

        verifyNoInteractions(pacemakerSetRepository, mapper);
    }

    @DisplayName("일일 제한 횟수를 조회한다. 생성했던 수 만큼 DAILY_LIMIT에서 차감되어 조회된다.")
    @Test
    void getRateLimitCounter() {
        // given
        Long dailyCount = PacemakerService.DAILY_LIMIT;
        when(redisRunningRepository.get(anyString())).thenReturn("1");

        // when
        Long remainedRateLimitCount = pacemakerService.getRateLimitCounter("Mock Member Uuid");

        // then
        assertThat(remainedRateLimitCount).isEqualTo(dailyCount - 1);
    }

    @DisplayName("일일 제한 횟수를 조회한다. 혹여나 제한 횟수를 초과하였더라도 마이너스가 아닌 0으로 출력된다.")
    @Test
    void getExceedRateLimitCounter() {
        // given
        Long dailyCount = PacemakerService.DAILY_LIMIT;
        when(redisRunningRepository.get(anyString())).thenReturn("5");

        // when
        Long remainedRateLimitCount = pacemakerService.getRateLimitCounter("Mock Member Uuid");

        // then
        assertThat(remainedRateLimitCount).isEqualTo(0);
    }

    @DisplayName("아직 페이스메이커를 생성하지 않았다면 DAILY_LIMIT이 출력된다.")
    @Test
    void getDailyMaxLimitCountWhenNotCreatePacemaker() {
        // given
        Long dailyCount = PacemakerService.DAILY_LIMIT;
        when(redisRunningRepository.get(anyString())).thenReturn(null);

        // when
        Long remainedRateLimitCount = pacemakerService.getRateLimitCounter("Mock Member Uuid");

        // then
        assertThat(remainedRateLimitCount).isEqualTo(dailyCount);
    }

}
