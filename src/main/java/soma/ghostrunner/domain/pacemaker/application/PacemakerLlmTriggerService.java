package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;

/**
 * 비동기 LLM 처리 오케스트레이션
 * - TX2(PROCEEDING) 상태 업데이트 위임
 * - LLM 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerLlmTriggerService {

    private final PacemakerStatusService statusService;
    private final PacemakerRateLimitService rateLimitService;
    private final PacemakerLlmService llmService;

    /**
     * 비동기 처리: TX2(PROCEEDING) + LLM 호출
     * - 새 트랜잭션에서 PROCEEDING 상태 업데이트
     * - 트랜잭션 커밋 후 LLM 호출
     * - TX2 실패 시 INIT 상태 유지 → 워커가 복구
     */
    @Async("llmTaskExecutor")
    public void processAsync(PacemakerCreationResult result) {
        Long pacemakerId = result.getPacemakerId();
        log.info("비동기 처리 시작 - pacemakerId={}", pacemakerId);

        // TX2: 새 트랜잭션에서 PROCEEDING 상태 업데이트
        try {
            statusService.updateToProceeding(pacemakerId);
        } catch (Exception e) {
            log.error("TX2 실패, INIT 상태 유지 - pacemakerId={}, 워커가 복구 예정", pacemakerId, e);
            return;  // LLM 호출하지 않음, 워커가 INIT 상태를 복구
        }

        // 트랜잭션 밖에서 LLM 호출
        requestLlm(result);
    }

    private void requestLlm(PacemakerCreationResult result) {
        String memberUuid = result.getMember().getUuid();
        String rateLimitKey = rateLimitService.createRateLimitKey(memberUuid);

        llmService.requestLlmToCreatePacemaker(
                result.getMember(),
                result.getWorkoutDto(),
                result.getVdot(),
                result.getCondition(),
                result.getTemperature(),
                result.getPacemakerId(),
                rateLimitKey
        );
    }

}
