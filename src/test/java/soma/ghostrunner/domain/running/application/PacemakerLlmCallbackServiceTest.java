package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.domain.events.PacemakerCreatedEvent;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;

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
    private RedisRunningRepository redisRunningRepository;

    @Mock
    private RunningApplicationMapper mapper;

    @Mock
    private ApplicationEventPublisher publisher;

    @Test
    void handleSuccess_shouldUpdateEntitiesAndRedis() {
        // given
        Long pacemakerId = 100L;
        String workoutJson = "{...any json...}";

        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        WorkoutDto workoutDto = mock(WorkoutDto.class);
        List<WorkoutSetDto> setDtos = List.of(mock(WorkoutSetDto.class));
        when(workoutDto.getSets()).thenReturn(setDtos);

        PacemakerSet setEntity = mock(PacemakerSet.class);
        List<PacemakerSet> sets = List.of(setEntity);
        PacemakerCreatedEvent event = mock(PacemakerCreatedEvent.class);
        when(mapper.toPacemakerCreatedEvent(any())).thenReturn(event);
        doNothing().when(publisher).publishEvent(event);

        try (MockedStatic<WorkoutDto> workoutDtoStatic = mockStatic(WorkoutDto.class);
             MockedStatic<PacemakerSet> pacemakerSetStatic = mockStatic(PacemakerSet.class)) {

            workoutDtoStatic.when(() ->
                            WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(anyString()))
                    .thenReturn(workoutDto);

            pacemakerSetStatic.when(() ->
                            PacemakerSet.createPacemakerSets(eq(setDtos), eq(pacemaker)))
                    .thenReturn(sets);

            // when
            assertThatNoException()
                    .isThrownBy(() -> service.handleSuccess(pacemakerId, workoutJson));

            // then
            // 1) 도메인 업데이트
            verify(pacemakerRepository).findById(pacemakerId);
            verify(pacemakerRepository).save(pacemaker);

            // 2) 세트 저장
            verify(pacemakerSetRepository).saveAll(sets);
        }
    }

    @Test
    void handleError_shouldCompensateAndUpdateFailedStatus() {
        // given
        String rateLimitKey = "rl:member:1";
        Long pacemakerId = 200L;

        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        // when
        assertThatNoException()
                .isThrownBy(() -> service.handleError(rateLimitKey, pacemakerId));

        // then
        // 1) 레이트리밋 보상
        verify(redisRunningRepository).decrementRateLimitCounter(rateLimitKey);

        // 2) 상태 업데이트 (FAILED)
        verify(pacemakerRepository).findById(pacemakerId);
        verify(pacemaker).updateStatus(Pacemaker.Status.FAILED);
    }

}
