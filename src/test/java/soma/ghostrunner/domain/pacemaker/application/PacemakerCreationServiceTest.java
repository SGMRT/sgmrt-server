package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerValidator;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.domain.pacemaker.api.support.PacemakerType;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.domain.formula.WorkoutType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerCreationServiceTest {

    @Mock
    PacemakerValidator validator;
    @Mock
    MemberService memberService;
    @Mock
    VdotService vdotService;
    @Mock
    WorkoutService workoutService;
    @Mock
    PacemakerRepository pacemakerRepository;
    @Mock
    PacemakerSetRepository pacemakerSetRepository;

    PacemakerCreationService creationService;

    @BeforeEach
    void setUp() {
        creationService = new PacemakerCreationService(
                validator,
                memberService,
                vdotService,
                workoutService,
                pacemakerRepository,
                pacemakerSetRepository
        );
    }

    @DisplayName("TX1: Rule-Base Pacemaker(INIT)와 PacemakerSet을 저장하고 결과를 반환한다")
    @Test
    void createInitialPacemaker_success() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;
        int vdot = 45;

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        List<WorkoutSetDto> workoutSets = List.of(
                WorkoutSetDto.of(1, WorkoutType.E, "5:30", 0.0, 2.0),
                WorkoutSetDto.of(2, WorkoutType.I, "4:40", 2.0, 8.0),
                WorkoutSetDto.of(3, WorkoutType.E, "5:30", 8.0, 10.0)
        );
        WorkoutDto workoutDto = WorkoutDto.of(RunningType.I, 10.0, workoutSets, 50);

        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);
        doNothing().when(validator).validateCreationRequest(courseId);
        when(memberService.findMemberVdot(memberUuid)).thenReturn(vdot);
        when(vdotService.getExpectedPacesByVdot(vdot))
                .thenReturn(Map.of(RunningType.I, 4.40, RunningType.E, 5.30));
        when(workoutService.generateWorkouts(eq(10.0), eq(RunningType.I), any()))
                .thenReturn(workoutDto);
        when(pacemakerRepository.save(any(Pacemaker.class)))
                .thenAnswer(invocation -> {
                    Pacemaker p = invocation.getArgument(0);
                    // 저장 후 ID 설정 시뮬레이션
                    return p;
                });

        // when
        PacemakerCreationResult result = creationService.createInitialPacemaker(memberUuid, command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMember()).isEqualTo(member);
        assertThat(result.getWorkoutDto()).isEqualTo(workoutDto);
        assertThat(result.getVdot()).isEqualTo(vdot);
        assertThat(result.getCondition()).isEqualTo(3);
        assertThat(result.getTemperature()).isEqualTo(25);

        // Pacemaker가 INIT 상태로 저장되었는지 확인
        ArgumentCaptor<Pacemaker> pacemakerCaptor = ArgumentCaptor.forClass(Pacemaker.class);
        verify(pacemakerRepository).save(pacemakerCaptor.capture());
        Pacemaker savedPacemaker = pacemakerCaptor.getValue();
        assertThat(savedPacemaker.getStatus()).isEqualTo(Pacemaker.Status.INIT);
        assertThat(savedPacemaker.getGoalDistance()).isEqualTo(10.0);
        assertThat(savedPacemaker.getRunningType()).isEqualTo(RunningType.I);

        // PacemakerSet이 저장되었는지 확인
        ArgumentCaptor<List<PacemakerSet>> setsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pacemakerSetRepository).saveAll(setsCaptor.capture());
        List<PacemakerSet> savedSets = setsCaptor.getValue();
        assertThat(savedSets).hasSize(3);
        // Rule-Base이므로 message는 null
        assertThat(savedSets.get(0).getMessage()).isNull();
    }

    @DisplayName("TX1: VDOT이 없으면 InvalidRunningException을 던진다")
    @Test
    void createInitialPacemaker_noVdot_throwsException() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);
        doNothing().when(validator).validateCreationRequest(courseId);
        when(memberService.findMemberVdot(memberUuid))
                .thenThrow(new MemberNotFoundException(ErrorCode.ENTITY_NOT_FOUND, "VDOT not found"));

        // when & then
        assertThatThrownBy(() -> creationService.createInitialPacemaker(memberUuid, command))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessageContaining("VDOT");

        // Repository 호출이 없어야 함
        verifyNoInteractions(pacemakerRepository, pacemakerSetRepository);
    }

    @DisplayName("TX1: 코스 검증 실패 시 예외가 전파된다")
    @Test
    void createInitialPacemaker_invalidCourse_throwsException() {
        // given
        String memberUuid = "member-123";
        Long courseId = 999L;

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);
        doThrow(new RuntimeException("Course not found"))
                .when(validator).validateCreationRequest(courseId);

        // when & then
        assertThatThrownBy(() -> creationService.createInitialPacemaker(memberUuid, command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Course not found");

        verifyNoInteractions(pacemakerRepository, pacemakerSetRepository);
    }

}
