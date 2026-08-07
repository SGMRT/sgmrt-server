package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.domain.Member;
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
class PacemakerCommandServiceTest {

    @Mock
    PacemakerCreationService creationService;
    @Mock
    PacemakerLlmTriggerService llmTriggerService;
    @Mock
    PacemakerUpdateService updateService;
    @Mock
    PacemakerRateLimitService rateLimitService;

    PacemakerCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new PacemakerCommandService(
                creationService,
                llmTriggerService,
                updateService,
                rateLimitService
        );
    }

    // ==================== 생성 테스트 ====================

    @DisplayName("페이스메이커 생성: 선카운트 → TX1 → 비동기 처리 전달 순서로 실행된다")
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

        when(rateLimitService.incrementCounter(memberUuid)).thenReturn("rate-limit-key");
        when(creationService.createInitialPacemaker(memberUuid, command)).thenReturn(result);

        // when
        Long actualId = commandService.createPacemaker(memberUuid, command);

        // then
        assertThat(actualId).isEqualTo(expectedPacemakerId);

        // 실행 순서 검증: 선카운트 → TX1 → 비동기 처리 전달 (TX2 + LLM은 비동기에서 처리)
        InOrder inOrder = inOrder(rateLimitService, creationService, llmTriggerService);
        inOrder.verify(rateLimitService).incrementCounter(memberUuid);
        inOrder.verify(creationService).createInitialPacemaker(memberUuid, command);
        inOrder.verify(llmTriggerService).processAsync(result);
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
                .when(rateLimitService).incrementCounter(memberUuid);

        // when & then
        assertThatThrownBy(() -> commandService.createPacemaker(memberUuid, command))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessageContaining("일일 사용량");

        // TX1, TX2, LLM 모두 호출되지 않아야 함
        verifyNoInteractions(creationService);
        verifyNoInteractions(llmTriggerService);
    }

    @DisplayName("TX1 실패 시 비동기 처리가 실행되지 않고, 카운트 보상이 실행된다")
    @Test
    void createPacemaker_tx1Fails_noAsync_andCompensates() {
        // given
        String memberUuid = "member-123";
        Long courseId = 1L;
        String rateLimitKey = "rate-limit-key";

        PacemakerCreateCommand command = new PacemakerCreateCommand(
                PacemakerType.STAMINA, 10.0, 3, 25, courseId);

        when(rateLimitService.incrementCounter(memberUuid)).thenReturn(rateLimitKey);
        when(creationService.createInitialPacemaker(memberUuid, command))
                .thenThrow(new RuntimeException("TX1 실패"));

        // when & then
        assertThatThrownBy(() -> commandService.createPacemaker(memberUuid, command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("TX1 실패");

        // 비동기 처리는 호출되지 않아야 함
        verifyNoInteractions(llmTriggerService);

        // 카운트 보상이 실행되어야 함
        verify(rateLimitService).decrementCounter(rateLimitKey);
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
        commandService.updateAfterRunning(memberUuid, pacemakerId, runningId);

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
        commandService.deletePacemaker(memberUuid, pacemakerId);

        // then
        verify(updateService).deletePacemaker(memberUuid, pacemakerId);
    }

}
