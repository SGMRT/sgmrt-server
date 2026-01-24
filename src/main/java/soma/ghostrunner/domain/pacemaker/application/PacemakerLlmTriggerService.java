package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.global.error.ErrorCode;

/**
 * TX2: LLM 요청 트리거 담당
 * - PROCEEDING 상태로 업데이트
 * - TX2 커밋 후 Redis 카운트 증가
 * - 비동기 LLM 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerLlmTriggerService {

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerRateLimitService rateLimitService;
    private final PacemakerLlmService llmService;

    /**
     * TX2: PROCEEDING 상태로 업데이트
     */
    @Transactional
    public void updateToProceeding(Long pacemakerId) {
        log.info("TX2 시작: PROCEEDING 상태 업데이트 - pacemakerId={}", pacemakerId);

        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.proceed();

        log.info("TX2 완료: PROCEEDING 상태 업데이트 완료 - pacemakerId={}", pacemakerId);
    }

    /**
     * TX2 커밋 후 호출: Redis 카운트 증가 + LLM 비동기 호출
     */
    public void triggerLlmAfterCommit(PacemakerCreationResult result) {
        String memberUuid = result.getMember().getUuid();
        String rateLimitKey = rateLimitService.createRateLimitKey(memberUuid);

        // Redis 카운트 증가 (DB 커밋 후이므로 안전)
        rateLimitService.incrementCounter(memberUuid);

        // 비동기 LLM 호출
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

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

}
