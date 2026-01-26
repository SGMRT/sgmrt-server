package soma.ghostrunner.domain.pacemaker.application;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    @Mock
    CircuitBreaker circuitBreaker;

    PacemakerLlmTriggerService triggerService;

    @BeforeEach
    void setUp() {
        triggerService = new PacemakerLlmTriggerService(
                statusService,
                rateLimitService,
                llmService,
                circuitBreaker
        );
    }

    private PacemakerCreationResult createTestResult() {
        String memberUuid = "member-123";
        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        return PacemakerCreationResult.builder()
                .pacemakerId(100L)
                .member(member)
                .workoutDto(WorkoutDto.of(RunningType.I, 10.0, List.of()))
                .vdot(45)
                .condition(3)
                .temperature(25)
                .build();
    }

    @Nested
    @DisplayName("Circuit CLOSED 상태")
    class WhenCircuitClosed {

        @BeforeEach
        void setUp() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        }

        @DisplayName("정상적으로 PROCEEDING 업데이트 후 LLM 호출한다")
        @Test
        void processAsync_updatesToProceedingAndCallsLlm() {
            // given
            PacemakerCreationResult result = createTestResult();
            when(rateLimitService.createRateLimitKey(anyString())).thenReturn("rate-limit-key");

            // when
            triggerService.processAsync(result);

            // then
            verify(statusService).updateToProceeding(result.getPacemakerId());
            verify(llmService).requestLlmToCreatePacemaker(
                    eq(result.getMember()),
                    eq(result.getWorkoutDto()),
                    eq(result.getVdot()),
                    eq(result.getCondition()),
                    eq(result.getTemperature()),
                    eq(result.getPacemakerId()),
                    anyString()
            );
        }

        @DisplayName("TX2 실패 시 LLM 호출하지 않고 워커가 복구하도록 둔다")
        @Test
        void processAsync_whenTx2Fails_shouldNotCallLlm() {
            // given
            PacemakerCreationResult result = createTestResult();
            doThrow(new RuntimeException("TX2 실패"))
                    .when(statusService).updateToProceeding(result.getPacemakerId());

            // when & then
            assertThatNoException().isThrownBy(() -> triggerService.processAsync(result));
            verifyNoInteractions(llmService);
        }
    }

    @Nested
    @DisplayName("Circuit OPEN 상태")
    class WhenCircuitOpen {

        @BeforeEach
        void setUp() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        }

        @DisplayName("TX2를 스킵하고 바로 FALLBACK 처리한다")
        @Test
        void processAsync_shouldSkipTx2AndFallback() {
            // given
            PacemakerCreationResult result = createTestResult();

            // when
            triggerService.processAsync(result);

            // then
            verify(statusService, never()).updateToProceeding(anyLong());
            verify(statusService).updateToFallback(result.getPacemakerId());
            verifyNoInteractions(llmService);
        }
    }

    @Nested
    @DisplayName("Circuit HALF_OPEN 상태")
    class WhenCircuitHalfOpen {

        @BeforeEach
        void setUp() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);
        }

        @DisplayName("테스트 요청으로 정상 처리한다 (CLOSED와 동일)")
        @Test
        void processAsync_shouldProcessNormally() {
            // given
            PacemakerCreationResult result = createTestResult();
            when(rateLimitService.createRateLimitKey(anyString())).thenReturn("rate-limit-key");

            // when
            triggerService.processAsync(result);

            // then
            verify(statusService).updateToProceeding(result.getPacemakerId());
            verify(llmService).requestLlmToCreatePacemaker(
                    any(), any(), anyInt(), anyInt(), anyInt(), anyLong(), anyString()
            );
        }
    }

}
