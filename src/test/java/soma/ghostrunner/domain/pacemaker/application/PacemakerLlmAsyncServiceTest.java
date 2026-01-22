package soma.ghostrunner.domain.pacemaker.application;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.domain.formula.WorkoutType;
import soma.ghostrunner.domain.pacemaker.infra.openai.PacemakerOpenAiRestClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerLlmAsyncServiceTest {

    @Mock
    private PacemakerOpenAiRestClient llmClient;

    @Mock
    private PacemakerLlmCallbackService callbackService;

    @InjectMocks
    private PacemakerLlmService service;

    @Test
    void requestLlmToCreatePacemaker_success_shouldCallHandleSuccess() {
        // given
        Member member = createMember("이복둥");
        int vdot = 50;
        int condition = 3;
        int temperature = 20;
        Long pacemakerId = 1L;
        String rateLimitKey = "rate-limit-key";

        RunningType runningType = RunningType.E;
        WorkoutSetDto set1 = createWorkoutSetDto();
        WorkoutDto workoutDto = createWorkoutDto(runningType, set1);

        String improvedWorkoutJson = createImprovedWorkoutJson();

        String completedWorkoutStr = "final-voice-guidance";

        when(llmClient.improveWorkout(anyString())).thenReturn(improvedWorkoutJson);
        when(llmClient.fillVoiceGuidance(anyString())).thenReturn(completedWorkoutStr);

        // when
        service.requestLlmToCreatePacemaker(
                member, workoutDto, vdot, condition, temperature, pacemakerId, rateLimitKey
        );

        // then
        verify(callbackService, times(1))
                .handleSuccess(eq(pacemakerId), eq(completedWorkoutStr));
        verify(callbackService, never())
                .handleError(anyString(), anyLong());
    }

    private @NotNull String createImprovedWorkoutJson() {
        return """
                {
                  "type": "E",
                  "goal_km": 10.0,
                  "expected_minutes": 60,
                  "sets": [
                    {
                      "setNum": 1,
                      "type": "E",
                      "pace_min/km": "5:30",
                      "start_km": 0.0,
                      "end_km": 10.0,
                      "feedback": null
                    }
                  ]
                }
                """;
    }

    private WorkoutDto createWorkoutDto(RunningType runningType, WorkoutSetDto set1) {
        return WorkoutDto.of(
                runningType,
                10.0,
                List.of(set1),
                60
        );
    }

    private WorkoutSetDto createWorkoutSetDto() {
        return WorkoutSetDto.of(
                1,
                WorkoutType.E,
                "5:30",
                0.0,
                10.0
        );
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    @Test
    void requestLlmToCreatePacemaker_failOnImproveWorkout_shouldCallHandleError() {
        // given
        Member member = createMember("이복둥");
        int vdot = 50;
        int condition = 3;
        int temperature = 20;
        Long pacemakerId = 2L;
        String rateLimitKey = "rate-limit-key-2";

        RunningType runningType = RunningType.E;
        WorkoutSetDto set1 = createWorkoutSetDto();
        WorkoutDto workoutDto = createWorkoutDto(runningType, set1);

        when(llmClient.improveWorkout(anyString()))
                .thenThrow(new RuntimeException("LLM error"));

        // when
        service.requestLlmToCreatePacemaker(
                member, workoutDto, vdot, condition, temperature, pacemakerId, rateLimitKey
        );

        // then
        verify(callbackService, times(1))
                .handleError(eq(rateLimitKey), eq(pacemakerId));
        verify(callbackService, never())
                .handleSuccess(anyLong(), anyString());
    }


}
