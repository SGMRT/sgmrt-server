package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.domain.llm.PacemakerLlmClient;
import soma.ghostrunner.domain.running.domain.llm.PacemakerPromptGenerator;
import soma.ghostrunner.domain.running.infra.openai.PacemakerTaskTracker;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerLlmService {

    private final PacemakerLlmClient llmClient;
    private final PacemakerTaskTracker taskTracker;
    private final PacemakerLlmCallbackService callbackService;

    public void requestLlmToCreatePacemaker(Member member, WorkoutDto workoutDto,
                                            int vdot, int condition, int temperature, Long pacemakerId,
                                            String rateLimitKey) {

        taskTracker.startTask();

        String workoutImprovementPrompt = PacemakerPromptGenerator.generateWorkoutImprovementPrompt(
                member, vdot, condition, temperature, workoutDto
        );

        llmClient.improveWorkout(workoutImprovementPrompt)
                .doOnSubscribe(s -> log.info("🔄 AI 고스트(페이스메이커) [{}]에 대한 LLM API 호출 시작", pacemakerId))
                .map(WorkoutDto::fromProcessedWorkoutDto)
                .flatMap(
                        dto -> {
                            log.info("🔄 AI 고스트(페이스메이커) [{}]에 대한 훈련표 생성 완료.. 음성 안내 생성 시작", pacemakerId);
                            String voicePrompt = PacemakerPromptGenerator.generateVoiceGuidancePrompt(
                                    member, vdot, condition, temperature, dto);
                            return llmClient.fillVoiceGuidance(voicePrompt);
                        })
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            handleSuccess(pacemakerId, result);
                        },
                        error -> {
                            handleError(pacemakerId, rateLimitKey, error);
                        }
                );
    }

    private void handleSuccess(Long pacemakerId, String result) {
        try {
            log.info("✅ AI 고스트(페이스메이커) [{}]에 대한 음성 안내 생성 완료.. DB 저장 시작", pacemakerId);
            callbackService.handleSuccess(pacemakerId, result);
        } catch (Exception e) {
            log.error("💥 AI 고스트(페이스메이커) [{}]의 결과 저장 중 에러 발생: {}", pacemakerId, e.getMessage());
        } finally {
            taskTracker.endTask();
            log.debug("🏁 AI 고스트(페이스메이커) [{}] 작업 완전히 종료 (성공 경로)", pacemakerId);
        }
    }

    private void handleError(Long pacemakerId, String rateLimitKey, Throwable error) {
        try {
            log.error("🚫 AI 고스트(페이스메이커) [{}] 생성 실패.. 실패 처리 시작: {}", pacemakerId, error.getMessage());
            callbackService.handleError(rateLimitKey, pacemakerId);
        } catch (Exception e) {
            log.error("💥 AI 고스트(페이스메이커) [{}] 에러 처리 중 에러 발생: {}", pacemakerId, e.getMessage());
        } finally {
            taskTracker.endTask();
            log.debug("🏁 AI 고스트(페이스메이커) [{}] 작업 완전히 종료 (실패 경로)", pacemakerId);
        }
    }

}
