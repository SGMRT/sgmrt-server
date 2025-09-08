package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    @Test
    void handleSuccess_shouldUpdateEntitiesAndRedis() {
        // given
        Member member = mock(Member.class);

        Long pacemakerId = 100L;
        String workoutJson = "{...any json...}";

        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        WorkoutDto workoutDto = mock(WorkoutDto.class);
        List<WorkoutSetDto> setDtos = List.of(mock(WorkoutSetDto.class));
        when(workoutDto.getSets()).thenReturn(setDtos);

        PacemakerSet setEntity = mock(PacemakerSet.class);
        List<PacemakerSet> sets = List.of(setEntity);

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
                    .isThrownBy(() -> service.handleSuccess(pacemakerId, workoutJson, member));

            // then
            // 1) 도메인 업데이트
            verify(pacemakerRepository).findById(pacemakerId);
            verify(pacemaker).updateSucceedPacemaker(workoutDto);
            verify(pacemakerRepository).save(pacemaker);

            // 2) 세트 저장
            verify(pacemakerSetRepository).saveAll(sets);

            // 3) Redis 상태 저장 (SUCCEED)
            ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<TimeUnit> unitCap = ArgumentCaptor.forClass(TimeUnit.class);
            ArgumentCaptor<Long> timeoutCap = ArgumentCaptor.forClass(Long.class);

            verify(redisRunningRepository).save(
                    keyCap.capture(),
                    valCap.capture(),
                    unitCap.capture(),
                    timeoutCap.capture()
            );

            // AssertJ 검증
            String expectedKeyPrefix = "pacemaker_processing_state:";
            org.assertj.core.api.Assertions.assertThat(keyCap.getValue())
                    .isEqualTo(expectedKeyPrefix + pacemakerId);
            org.assertj.core.api.Assertions.assertThat(valCap.getValue())
                    .isEqualTo("null:SUCCEED");
            org.assertj.core.api.Assertions.assertThat(unitCap.getValue())
                    .isEqualTo(TimeUnit.DAYS);
            org.assertj.core.api.Assertions.assertThat(timeoutCap.getValue())
                    .isEqualTo(1L);
        }
    }

    @Test
    void handleError_shouldCompensateAndUpdateFailedStatus() {
        // given
        Member member = mock(Member.class);

        String rateLimitKey = "rl:member:1";
        Long pacemakerId = 200L;

        Pacemaker pacemaker = mock(Pacemaker.class);
        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        // when
        assertThatNoException()
                .isThrownBy(() -> service.handleError(rateLimitKey, pacemakerId, member));

        // then
        // 1) 레이트리밋 보상
        verify(redisRunningRepository).decrementRateLimitCounter(rateLimitKey);

        // 2) 상태 업데이트 (FAILED)
        verify(pacemakerRepository).findById(pacemakerId);
        verify(pacemaker).updateStatus(Pacemaker.Status.FAILED);

        // 3) Redis 상태 저장 (FAILED)
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TimeUnit> unitCap = ArgumentCaptor.forClass(TimeUnit.class);
        ArgumentCaptor<Long> timeoutCap = ArgumentCaptor.forClass(Long.class);

        verify(redisRunningRepository).save(
                keyCap.capture(),
                valCap.capture(),
                unitCap.capture(),
                timeoutCap.capture()
        );

        String expectedKeyPrefix = "pacemaker_processing_state:";
        org.assertj.core.api.Assertions.assertThat(keyCap.getValue())
                .isEqualTo(expectedKeyPrefix + pacemakerId);
        org.assertj.core.api.Assertions.assertThat(valCap.getValue())
                .isEqualTo("null:FAILED");
        org.assertj.core.api.Assertions.assertThat(unitCap.getValue())
                .isEqualTo(TimeUnit.DAYS);
        org.assertj.core.api.Assertions.assertThat(timeoutCap.getValue())
                .isEqualTo(1L);
    }
}
