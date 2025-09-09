package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;
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

    public void requestLlmToCreatePacemaker(Member member, WorkoutDto workoutDto,
                                            int vdot, int condition, int temperature, Long pacemakerId,
                                            String rateLimitKey) {

        String workoutImprovementPrompt = PacemakerPromptGenerator.generateWorkoutImprovementPrompt(
                member, vdot, condition, temperature, workoutDto
        );

        llmClient.improveWorkout(workoutImprovementPrompt)
                .doOnSubscribe(s -> log.info("🔄 [{}]에 대한 LLM API 호출 시작", pacemakerId))          // 이벤트루프 스레드
                .map(WorkoutDto::fromProcessedWorkoutDto)
                .flatMap(
                        dto -> {
                            String voicePrompt = PacemakerPromptGenerator.generateVoiceGuidancePrompt(
                                    member, vdot, condition, temperature, dto);
                            return llmClient.fillVoiceGuidance(voicePrompt);
                        })
                .publishOn(Schedulers.boundedElastic())         // elastic 스레드
                .subscribe(
                        result -> {
                            log.info("✅ [{}]에 대한 LLM API 요청을 성공했습니다.", pacemakerId);
                            callbackService.handleSuccess(pacemakerId, result);
                        },
                        error -> {
                            log.error("🚫 [{}]에 대한 LLM API 요청을 실패했습니다. : {}", pacemakerId, error.getMessage());
                            callbackService.handleError(rateLimitKey, pacemakerId);
                        }
                );
    }

}
