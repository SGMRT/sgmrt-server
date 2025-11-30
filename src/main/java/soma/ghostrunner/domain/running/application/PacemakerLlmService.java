package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.domain.llm.PacemakerLlmClient;
import soma.ghostrunner.domain.running.domain.llm.PacemakerPromptGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerLlmService {

    private final PacemakerLlmClient llmClient;
    private final PacemakerLlmCallbackService callbackService;

    @Async("llmTaskExecutor")
    public void requestLlmToCreatePacemaker(Member member, WorkoutDto workoutDto,
                                            int vdot, int condition, int temperature, Long pacemakerId,
                                            String rateLimitKey) {

        try {

            String workoutImprovementPrompt = PacemakerPromptGenerator.generateWorkoutImprovementPrompt(
                    member, vdot, condition, temperature, workoutDto
            );

            log.info("🔄 AI 고스트(페이스메이커) [{}]에 대한 LLM API (훈련표 개선) 호출 시작", pacemakerId);

            String improvedWorkoutStr = llmClient.improveWorkout(workoutImprovementPrompt);
            WorkoutDto improvedWorkout = WorkoutDto.fromProcessedWorkoutDto(improvedWorkoutStr);

            log.info("🔄 AI 고스트(페이스메이커) [{}] 훈련표 생성 완료.. 음성 안내 생성 시작", pacemakerId);

            String voicePrompt = PacemakerPromptGenerator.generateVoiceGuidancePrompt(
                    member, vdot, condition, temperature, improvedWorkout
            );
            String completedWorkoutStr = llmClient.fillVoiceGuidance(voicePrompt);

            log.info("✅ AI 고스트(페이스메이커) [{}]에 대한 음성 안내 생성 완료..", pacemakerId);
            callbackService.handleSuccess(pacemakerId, completedWorkoutStr);

        } catch (Exception e) {

            log.error("🚫 AI 고스트(페이스메이커) [{}] 생성 실패.. 에러 메세지 : {}", pacemakerId, e.getMessage());
            callbackService.handleError(rateLimitKey, pacemakerId);

        }

    }

}
