package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerLlmTriggerServiceTest {

    @Mock
    PacemakerStatusService statusService;
    @Mock
    PacemakerRateLimitService rateLimitService;
    @Mock
    PacemakerLlmService llmService;

    PacemakerLlmTriggerService triggerService;

    @BeforeEach
    void setUp() {
        triggerService = new PacemakerLlmTriggerService(
                statusService,
                rateLimitService,
                llmService
        );
    }

    @DisplayName("processAsync: PROCEEDING 업데이트 후 LLM 호출한다")
    @Test
    void processAsync_updatesToProceedingAndCallsLlm() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;
        int vdot = 45;
        int condition = 3;
        int temperature = 25;
        String rateLimitKey = "pacemaker_api_rate_limit:member-123:2024-01-01";

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        WorkoutDto workoutDto = WorkoutDto.of(RunningType.I, 10.0, List.of());

        PacemakerCreationResult result = PacemakerCreationResult.builder()
                .pacemakerId(pacemakerId)
                .member(member)
                .workoutDto(workoutDto)
                .vdot(vdot)
                .condition(condition)
                .temperature(temperature)
                .build();

        when(rateLimitService.createRateLimitKey(memberUuid)).thenReturn(rateLimitKey);

        // when
        triggerService.processAsync(result);

        // then
        // 1. PROCEEDING 상태 업데이트 (새 트랜잭션)
        verify(statusService).updateToProceeding(pacemakerId);

        // 2. LLM 호출
        verify(llmService).requestLlmToCreatePacemaker(
                eq(member),
                eq(workoutDto),
                eq(vdot),
                eq(condition),
                eq(temperature),
                eq(pacemakerId),
                eq(rateLimitKey)
        );
    }

    @DisplayName("processAsync: TX2 실패 시 LLM 호출하지 않고 예외를 던지지 않는다 (워커가 복구)")
    @Test
    void processAsync_whenTx2Fails_shouldNotThrowAndNotCallLlm() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;

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

        // TX2 실패 시뮬레이션
        doThrow(new RuntimeException("TX2 실패"))
                .when(statusService).updateToProceeding(pacemakerId);

        // when & then - 예외를 던지지 않음
        assertThatNoException().isThrownBy(() -> triggerService.processAsync(result));

        // LLM은 호출되지 않아야 함
        verifyNoInteractions(llmService);
    }

}
