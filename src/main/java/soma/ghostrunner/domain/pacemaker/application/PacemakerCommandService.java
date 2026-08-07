package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;

/**
 * 페이스메이커 명령(생성·업데이트·삭제) 진입점 — 조회는 {@link PacemakerQueryService}
 *
 * 역할:
 * - 생성: TX1(Rule-Base, INIT) → 비동기(TX2 + LLM)
 * - 업데이트: 러닝 후 상태 업데이트, 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerCommandService {

    private final PacemakerCreationService creationService;
    private final PacemakerLlmTriggerService llmTriggerService;
    private final PacemakerUpdateService updateService;
    private final PacemakerRateLimitService rateLimitService;

    /**
     * 페이스메이커 생성
     *
     * 흐름:
     * 1. Rate Limit 선카운트 (원자적 증가 + 임계치 검증)
     * 2. TX1: Rule-Base Pacemaker(INIT) 생성
     * 3. 비동기 처리 전달 (TX2 + LLM 호출)
     *
     * 상태 의미:
     * - INIT: Rule-Base 생성 완료, 비동기 작업 대기 중
     * - PROCEEDING: LLM 통신 중
     * - COMPLETED: LLM 성공
     * - FALLBACK: LLM 실패, Rule-Base 제공
     *
     * @param memberUuid 회원 UUID
     * @param command 생성 요청 커맨드
     * @return 생성된 페이스메이커 ID
     */
    public Long createPacemaker(String memberUuid, PacemakerCreateCommand command) {

        log.info("페이스메이커 생성 시작 - memberUuid={}, courseId={}", memberUuid, command.getCourseId());

        // 선카운트: 원자적으로 카운트 증가 + 임계치 검증 (Race Condition 방지)
        String rateLimitKey = rateLimitService.createRateLimitKey(memberUuid);
        rateLimitService.incrementCounter(memberUuid);

        // TX1: Rule-Base Pacemaker(INIT) 생성 및 저장
        // TX1 실패 시에만 카운트 보상 (Pacemaker가 저장되지 않았으므로)
        PacemakerCreationResult result;
        try {
            result = creationService.createInitialPacemaker(memberUuid, command);
        } catch (Exception e) {
            log.warn("TX1 실패, 카운트 보상 처리 - memberUuid={}", memberUuid, e);
            rateLimitService.decrementCounter(rateLimitKey);
            throw e;
        }

        // 비동기 처리 전달 (TX2 + LLM 호출은 비동기 스레드에서 수행)
        llmTriggerService.processAsync(result);

        log.info("페이스메이커 생성 요청 완료 - pacemakerId={}", result.getPacemakerId());
        return result.getPacemakerId();
    }

    /**
     * 러닝 완료 후 페이스메이커 상태 업데이트
     */
    public void updateAfterRunning(String memberUuid, Long pacemakerId, Long runningId) {
        updateService.updateAfterRunning(memberUuid, pacemakerId, runningId);
    }

    /**
     * 페이스메이커 삭제
     */
    public void deletePacemaker(String memberUuid, Long pacemakerId) {
        updateService.deletePacemaker(memberUuid, pacemakerId);
    }

}
