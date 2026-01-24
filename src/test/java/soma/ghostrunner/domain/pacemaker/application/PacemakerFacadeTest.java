package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerInCourseViewPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.support.PacemakerType;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;

import java.util.List;

import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.error.ErrorCode;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerFacadeTest {

    @Mock
    PacemakerCreationService creationService;
    @Mock
    PacemakerLlmTriggerService llmTriggerService;
    @Mock
    PacemakerQueryService queryService;
    @Mock
    PacemakerUpdateService updateService;
    @Mock
    PacemakerRateLimitService rateLimitService;

    PacemakerFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PacemakerFacade(
                creationService,
                llmTriggerService,
                queryService,
                updateService,
                rateLimitService
        );
    }

    // ==================== 생성 테스트 ====================

    @DisplayName("페이스메이커 생성: Rate Limit 체크 → TX1 → TX2 → Redis + LLM 순서로 실행된다")
    @Test
    void createPacemaker_executesInCorrectOrder() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;
        Long expectedPacemakerId = 100L;

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        WorkoutDto workoutDto = WorkoutDto.of(RunningType.I, 10.0, List.of());
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, RunningType.I, memberUuid);

        PacemakerCreationResult result = PacemakerCreationResult.builder()
                .pacemakerId(expectedPacemakerId)
                .member(member)
                .workoutDto(workoutDto)
                .vdot(45)
                .condition(3)
                .temperature(25)
                .build();

        when(creationService.createInitialPacemaker(memberUuid, command)).thenReturn(result);

        // when
        Long actualId = facade.createPacemaker(memberUuid, command);

        // then
        assertThat(actualId).isEqualTo(expectedPacemakerId);

        // 실행 순서 검증: Rate Limit 체크 → TX1 → TX2 → triggerLlmAfterCommit
        InOrder inOrder = inOrder(rateLimitService, creationService, llmTriggerService);
        inOrder.verify(rateLimitService).validateRateLimit(memberUuid);
        inOrder.verify(creationService).createInitialPacemaker(memberUuid, command);
        inOrder.verify(llmTriggerService).updateToProceeding(expectedPacemakerId);
        inOrder.verify(llmTriggerService).triggerLlmAfterCommit(result);
    }

    @DisplayName("Rate Limit 초과 시 TX1, TX2, LLM이 실행되지 않는다 (Fail-Fast)")
    @Test
    void createPacemaker_rateLimitExceeded_noTxOrLlm() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        doThrow(new InvalidRunningException(ErrorCode.TOO_MANY_REQUESTS, "일일 사용량을 초과했습니다."))
                .when(rateLimitService).validateRateLimit(memberUuid);

        // when & then
        assertThatThrownBy(() -> facade.createPacemaker(memberUuid, command))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessageContaining("일일 사용량");

        // TX1, TX2, LLM 모두 호출되지 않아야 함
        verifyNoInteractions(creationService);
        verifyNoInteractions(llmTriggerService);
    }

    @DisplayName("TX1 실패 시 TX2와 LLM 호출이 실행되지 않는다")
    @Test
    void createPacemaker_tx1Fails_noTx2OrLlm() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        when(creationService.createInitialPacemaker(memberUuid, command))
                .thenThrow(new RuntimeException("TX1 실패"));

        // when & then
        assertThatThrownBy(() -> facade.createPacemaker(memberUuid, command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("TX1 실패");

        // TX2와 LLM은 호출되지 않아야 함
        verifyNoInteractions(llmTriggerService);
    }

    @DisplayName("TX2 실패 시 Redis와 LLM 호출이 실행되지 않는다")
    @Test
    void createPacemaker_tx2Fails_noLlm() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;
        Long pacemakerId = 100L;

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        PacemakerCreationResult result = PacemakerCreationResult.builder()
                .pacemakerId(pacemakerId)
                .member(member)
                .workoutDto(WorkoutDto.of(RunningType.I, 10.0, List.of()))
                .vdot(45)
                .condition(3)
                .temperature(25)
                .build();

        when(creationService.createInitialPacemaker(memberUuid, command)).thenReturn(result);
        doThrow(new RuntimeException("TX2 실패"))
                .when(llmTriggerService).updateToProceeding(pacemakerId);

        // when & then
        assertThatThrownBy(() -> facade.createPacemaker(memberUuid, command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("TX2 실패");

        // triggerLlmAfterCommit은 호출되지 않아야 함
        verify(llmTriggerService, never()).triggerLlmAfterCommit(any());
    }

    // ==================== 조회 테스트 ====================

    @DisplayName("페이스메이커 조회는 QueryService로 위임된다")
    @Test
    void getPacemaker_delegatesToQueryService() {
        // given
        Long pacemakerId = 100L;
        String memberUuid = "member-123";
        PacemakerPollingResponse expected = new PacemakerPollingResponse();

        when(queryService.getPacemaker(pacemakerId, memberUuid)).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = facade.getPacemaker(pacemakerId, memberUuid);

        // then
        assertThat(actual).isSameAs(expected);
        verify(queryService).getPacemaker(pacemakerId, memberUuid);
    }

    @DisplayName("코스 내 페이스메이커 조회는 QueryService로 위임된다")
    @Test
    void getPacemakerInCourse_delegatesToQueryService() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;
        PacemakerInCourseViewPollingResponse expected = new PacemakerInCourseViewPollingResponse();

        when(queryService.getPacemakerInCourse(memberUuid, courseId)).thenReturn(expected);

        // when
        PacemakerInCourseViewPollingResponse actual = facade.getPacemakerInCourse(memberUuid, courseId);

        // then
        assertThat(actual).isSameAs(expected);
        verify(queryService).getPacemakerInCourse(memberUuid, courseId);
    }

    // ==================== 업데이트/삭제 테스트 ====================

    @DisplayName("러닝 후 업데이트는 UpdateService로 위임된다")
    @Test
    void updateAfterRunning_delegatesToUpdateService() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;
        Long runningId = 200L;

        // when
        facade.updateAfterRunning(memberUuid, pacemakerId, runningId);

        // then
        verify(updateService).updateAfterRunning(memberUuid, pacemakerId, runningId);
    }

    @DisplayName("페이스메이커 삭제는 UpdateService로 위임된다")
    @Test
    void deletePacemaker_delegatesToUpdateService() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;

        // when
        facade.deletePacemaker(memberUuid, pacemakerId);

        // then
        verify(updateService).deletePacemaker(memberUuid, pacemakerId);
    }

    // ==================== Rate Limit 테스트 ====================

    @DisplayName("Rate Limit 조회는 RateLimitService로 위임된다")
    @Test
    void getRateLimitCounter_delegatesToRateLimitService() {
        // given
        String memberUuid = "member-123";
        Long expectedCount = 2L;

        when(rateLimitService.getRemainingCount(memberUuid)).thenReturn(expectedCount);

        // when
        Long count = facade.getRateLimitCounter(memberUuid);

        // then
        assertThat(count).isEqualTo(expectedCount);
        verify(rateLimitService).getRemainingCount(memberUuid);
    }

}
