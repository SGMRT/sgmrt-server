package soma.ghostrunner.domain.pacemaker.application;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.pacemaker.application.dto.RecoveryContext;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pacemaker 복구 서비스
 * - 서킷브레이커 상태 확인
 * - INIT/PROCEEDING 상태로 남아있는 Pacemaker를 조회하여 LLM 재호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerRecoveryService {

    private static final int THRESHOLD_MINUTES = 15;
    private static final int BATCH_SIZE = 10;

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerRecoveryPrepareService prepareService;
    private final PacemakerLlmService llmService;
    private final PacemakerStatusService statusService;
    private final CircuitBreaker circuitBreaker;

    /**
     * 복구 대상 ID 목록 조회
     */
    public List<Long> findRecoveryTargetIds() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(THRESHOLD_MINUTES);
        return pacemakerRepository.findRecoveryTargetIds(threshold, PageRequest.of(0, BATCH_SIZE));
    }

    /**
     * 단일 Pacemaker 복구
     * - 서킷브레이커가 열려있으면 FALLBACK 처리
     * - 상태 업데이트 + 데이터 준비 (REQUIRES_NEW 트랜잭션, 별도 서비스)
     * - LLM 재호출 (트랜잭션 밖)
     */
    public void recoverSingle(Long pacemakerId) {
        log.info("Pacemaker 복구 시작 - pacemakerId={}", pacemakerId);

        // 서킷브레이커 상태 확인
        if (isCircuitOpen()) {
            log.warn("서킷브레이커 OPEN 상태, FALLBACK 처리 - pacemakerId={}", pacemakerId);
            statusService.updateToFallback(pacemakerId);
            return;
        }

        // 1. 상태 업데이트 + 데이터 준비 (별도 서비스의 독립 트랜잭션)
        RecoveryContext context = prepareService.prepareForRecovery(pacemakerId);

        // 2. LLM 재호출 (트랜잭션 밖, rateLimitKey = null로 Rate Limit 복구 스킵)
        llmService.requestLlmToCreatePacemaker(
                context.member(),
                context.workoutDto(),
                context.vdot(),
                context.condition(),
                context.temperature(),
                context.pacemakerId(),
                null  // 워커 재시도는 Rate Limit 카운트 안 함
        );

        log.info("Pacemaker 복구 LLM 호출 완료 - pacemakerId={}", pacemakerId);
    }

    private boolean isCircuitOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

}
