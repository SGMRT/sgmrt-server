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
import soma.ghostrunner.domain.pacemaker.application.dto.RecoveryContext;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerRecoveryServiceTest {

    @Mock
    private PacemakerRepository pacemakerRepository;

    @Mock
    private PacemakerRecoveryPrepareService prepareService;

    @Mock
    private PacemakerLlmService llmService;

    @Mock
    private PacemakerStatusService statusService;

    @Mock
    private CircuitBreaker circuitBreaker;

    private PacemakerRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PacemakerRecoveryService(
                pacemakerRepository,
                prepareService,
                llmService,
                statusService,
                circuitBreaker
        );
    }

    @DisplayName("findRecoveryTargetIds: 복구 대상 ID 목록을 반환한다")
    @Test
    void findRecoveryTargetIds_shouldReturnIds() {
        // given
        when(pacemakerRepository.findRecoveryTargetIds(any(), any()))
                .thenReturn(List.of(1L, 2L, 3L));

        // when
        List<Long> result = service.findRecoveryTargetIds();

        // then
        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @DisplayName("findRecoveryTargetIds: 복구 대상이 없으면 빈 목록을 반환한다")
    @Test
    void findRecoveryTargetIds_whenNoTargets_shouldReturnEmptyList() {
        // given
        when(pacemakerRepository.findRecoveryTargetIds(any(), any()))
                .thenReturn(Collections.emptyList());

        // when
        List<Long> result = service.findRecoveryTargetIds();

        // then
        assertThat(result).isEmpty();
    }

    private RecoveryContext createMockRecoveryContext(Long pacemakerId) {
        Member member = mock(Member.class);
        WorkoutDto workoutDto = WorkoutDto.of(RunningType.R, 10.0, List.of());
        return new RecoveryContext(member, 45, workoutDto, 3, 20, pacemakerId);
    }

    @Nested
    @DisplayName("Circuit CLOSED 상태")
    class WhenCircuitClosed {

        @BeforeEach
        void setUp() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        }

        @DisplayName("prepareForRecovery 후 LLM을 호출한다")
        @Test
        void recoverSingle_shouldPrepareAndCallLlm() {
            // given
            Long pacemakerId = 1L;
            RecoveryContext context = createMockRecoveryContext(pacemakerId);
            when(prepareService.prepareForRecovery(pacemakerId)).thenReturn(context);

            // when
            service.recoverSingle(pacemakerId);

            // then
            verify(prepareService).prepareForRecovery(pacemakerId);
            verify(llmService).requestLlmToCreatePacemaker(
                    any(Member.class), any(), anyInt(), anyInt(), anyInt(), anyLong(), any()
            );
        }
    }

    @Nested
    @DisplayName("Circuit OPEN 상태")
    class WhenCircuitOpen {

        @BeforeEach
        void setUp() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        }

        @DisplayName("복구를 스킵하고 FALLBACK 처리한다")
        @Test
        void recoverSingle_shouldSkipAndFallback() {
            // given
            Long pacemakerId = 1L;

            // when
            service.recoverSingle(pacemakerId);

            // then
            verify(statusService).updateToFallback(pacemakerId);
            verifyNoInteractions(prepareService);
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

        @DisplayName("테스트 요청으로 정상 처리한다")
        @Test
        void recoverSingle_shouldProcessNormally() {
            // given
            Long pacemakerId = 1L;
            RecoveryContext context = createMockRecoveryContext(pacemakerId);
            when(prepareService.prepareForRecovery(pacemakerId)).thenReturn(context);

            // when
            service.recoverSingle(pacemakerId);

            // then
            verify(prepareService).prepareForRecovery(pacemakerId);
            verify(llmService).requestLlmToCreatePacemaker(
                    any(Member.class), any(), anyInt(), anyInt(), anyInt(), anyLong(), any()
            );
        }
    }

}
