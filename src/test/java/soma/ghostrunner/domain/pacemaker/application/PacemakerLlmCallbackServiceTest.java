package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerApplicationMapper;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.events.PacemakerCreatedEvent;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerLlmCallbackServiceTest {

    @InjectMocks
    private PacemakerLlmCallbackService service;

    @Mock
    private PacemakerRepository pacemakerRepository;

    @Mock
    private PacemakerSetRepository pacemakerSetRepository;

    @Mock
    private PacemakerRateLimitService rateLimitService;

    @Mock
    private PacemakerApplicationMapper mapper;

    @Mock
    private ApplicationEventPublisher publisher;

    @Test
    void handleSuccess_shouldUpdatePacemakerAndSets() {
        // given
        Long pacemakerId = 100L;
        String workoutJson = "{...any json...}";

        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        WorkoutSetDto setDto = mock(WorkoutSetDto.class);
        when(setDto.getMessage()).thenReturn("안내 메시지");

        WorkoutDto workoutDto = mock(WorkoutDto.class);
        List<WorkoutSetDto> setDtos = List.of(setDto);
        when(workoutDto.getSets()).thenReturn(setDtos);
        when(workoutDto.getSummary()).thenReturn("요약");
        when(workoutDto.getGoalKm()).thenReturn(10.0);
        when(workoutDto.getExpectedMinutes()).thenReturn(50);
        when(workoutDto.getInitialMessage()).thenReturn("초기 메시지");

        PacemakerSet existingSet = mock(PacemakerSet.class);
        List<PacemakerSet> existingSets = List.of(existingSet);
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId)).thenReturn(existingSets);

        PacemakerCreatedEvent event = mock(PacemakerCreatedEvent.class);
        when(mapper.toPacemakerCreatedEvent(any())).thenReturn(event);
        doNothing().when(publisher).publishEvent(event);

        try (MockedStatic<WorkoutDto> workoutDtoStatic = mockStatic(WorkoutDto.class)) {

            workoutDtoStatic.when(() ->
                            WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(anyString()))
                    .thenReturn(workoutDto);

            // when
            assertThatNoException()
                    .isThrownBy(() -> service.handleSuccess(pacemakerId, workoutJson));

            // then
            // 1) Pacemaker 상태 전이: PROCEEDING -> COMPLETED
            verify(pacemakerRepository).findById(pacemakerId);
            verify(pacemaker).complete("요약", 10.0, 50, "초기 메시지");

            // 2) 기존 PacemakerSet의 message 업데이트
            verify(pacemakerSetRepository).findByPacemakerIdOrderBySetNumAsc(pacemakerId);
            verify(existingSet).updateMessage("안내 메시지");

            // 3) 이벤트 발행
            verify(publisher).publishEvent(event);
        }
    }

    @Test
    void handleError_shouldCompensateRedisAndUpdateToFallback() {
        // given
        String rateLimitKey = "rl:member:1";
        Long pacemakerId = 200L;

        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        // when
        assertThatNoException()
                .isThrownBy(() -> service.handleError(rateLimitKey, pacemakerId));

        // then
        // 1) Redis 카운트 복구 (보상) - 사용자가 다시 시도할 수 있도록
        verify(rateLimitService).decrementCounter(rateLimitKey);

        // 2) 상태를 FALLBACK으로 전이 (Rule-Base 결과 제공)
        verify(pacemakerRepository).findById(pacemakerId);
        verify(pacemaker).fallback();
    }

}
