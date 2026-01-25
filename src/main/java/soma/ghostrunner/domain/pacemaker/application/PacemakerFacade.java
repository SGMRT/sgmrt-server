package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerInCourseViewPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;

/**
 * 페이스메이커 Facade - 모든 진입점
 *
 * 역할:
 * - 생성: TX1(Rule-Base) → TX2(PROCEEDING) → Redis + LLM
 * - 조회: 단건 조회, 코스 내 조회
 * - 업데이트: 러닝 후 상태 업데이트, 삭제
 * - Rate Limit: 남은 사용량 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerFacade {

    private final PacemakerCreationService creationService;
    private final PacemakerLlmTriggerService llmTriggerService;
    private final PacemakerQueryService queryService;
    private final PacemakerUpdateService updateService;
    private final PacemakerRateLimitService rateLimitService;

    // ==================== 생성 ====================

    /**
     * 페이스메이커 생성 (TX 분리)
     *
     * 흐름:
     * 1. Rate Limit 선카운트 (원자적 증가 + 임계치 검증)
     * 2. TX1: Rule-Base Pacemaker(INIT) 생성
     * 3. TX2: PROCEEDING 상태 업데이트
     * 4. 비동기 LLM 호출
     *
     * Race Condition 방지:
     * - 선카운트로 Redis에서 원자적으로 카운트 증가 및 임계치 검증
     * - TX1/TX2 실패 시 보상 트랜잭션으로 카운트 감소
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

        try {
            // TX1: Rule-Base Pacemaker(INIT) 생성 및 저장
            PacemakerCreationResult result = creationService.createInitialPacemaker(memberUuid, command);
            Long pacemakerId = result.getPacemakerId();

            // TX2: PROCEEDING 상태로 업데이트
            llmTriggerService.updateToProceeding(pacemakerId);

            // 비동기 LLM 호출
            llmTriggerService.triggerLlmAfterCommit(result);

            log.info("페이스메이커 생성 요청 완료 - pacemakerId={}", pacemakerId);
            return pacemakerId;

        } catch (Exception e) {
            log.warn("페이스메이커 생성 실패, 카운트 보상 처리 - memberUuid={}", memberUuid, e);
            compensateRateLimitCounter(rateLimitKey);
            throw e;
        }
    }

    /**
     * Rate Limit 카운트 보상 (실패 시 감소)
     */
    private void compensateRateLimitCounter(String rateLimitKey) {
        try {
            rateLimitService.decrementCounter(rateLimitKey);
        } catch (Exception compensationEx) {
            log.error("카운트 보상 트랜잭션 실패 - rateLimitKey={}", rateLimitKey, compensationEx);
            // 보상 실패는 사용자에게 유리한 방향(카운트 덜 소진)이므로 무시
        }
    }

    // ==================== 조회 ====================

    /**
     * 페이스메이커 단건 조회 (폴링용)
     */
    public PacemakerPollingResponse getPacemaker(Long pacemakerId, String memberUuid) {
        return queryService.getPacemaker(pacemakerId, memberUuid);
    }

    /**
     * 코스 내 페이스메이커 조회 (폴링용)
     */
    public PacemakerInCourseViewPollingResponse getPacemakerInCourse(String memberUuid, Long courseId) {
        return queryService.getPacemakerInCourse(memberUuid, courseId);
    }

    // ==================== 업데이트/삭제 ====================

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

    // ==================== Rate Limit ====================

    /**
     * 남은 일일 사용량 조회
     */
    public Long getRateLimitCounter(String memberUuid) {
        return rateLimitService.getRemainingCount(memberUuid);
    }

}
