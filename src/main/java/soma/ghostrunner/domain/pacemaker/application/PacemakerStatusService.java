package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pacemaker 상태 변경 담당 서비스
 * - REQUIRES_NEW로 독립 트랜잭션 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerStatusService {

    private final PacemakerRepository pacemakerRepository;

    /**
     * TX2: PROCEEDING 상태로 업데이트 (새 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateToProceeding(Long pacemakerId) {
        log.info("TX2 시작: PROCEEDING 상태 업데이트 - pacemakerId={}", pacemakerId);

        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.proceed();

        log.info("TX2 완료: PROCEEDING 상태 업데이트 완료 - pacemakerId={}", pacemakerId);
    }

    /**
     * FALLBACK 상태로 업데이트 (새 트랜잭션)
     * - 서킷브레이커가 열려있을 때 호출
     * - INIT 또는 PROCEEDING 상태에서 FALLBACK으로 전이
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateToFallback(Long pacemakerId) {
        log.info("FALLBACK 상태 업데이트 시작 - pacemakerId={}", pacemakerId);

        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.fallback();

        log.info("FALLBACK 상태 업데이트 완료 - pacemakerId={}", pacemakerId);
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    /**
     * 임계치는 graceful shutdown 대기(20분)·LLM 재시도 워스트(~18분)보다 길어야
     * 정상 진행 중인 작업을 고아로 오판하지 않는다.
     */
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(30);

    /**
     * 폴링 조회 전 지연 판정 — INIT/PROCEEDING인 채 임계치를 넘긴 고아 레코드를 FALLBACK(FAILED)으로
     * 전환해 사용자에게 Rule-Base 훈련표가 응답되도록 한다. SIGKILL·크래시 등으로 LLM 작업이 유실된
     * 경우의 안전망으로, 발생 시 error 로그로 개발자에게 알린다.
     *
     * REQUIRES_NEW — QueryService의 readOnly 트랜잭션 안에서 호출되므로, 참여하면 쓰기가 막힌다.
     * 독립 트랜잭션으로 먼저 커밋되어야 이어지는 조회가 전환된 상태를 읽는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallbackIfStale(Long pacemakerId) {
        pacemakerRepository.findById(pacemakerId).ifPresent(this::fallbackIfStale);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallbackIfStaleInCourse(Long courseId, String memberUuid) {
        pacemakerRepository.findByCourseId(courseId, memberUuid).ifPresent(this::fallbackIfStale);
    }

    private void fallbackIfStale(Pacemaker pacemaker) {
        boolean stale = pacemaker.isNotCompleted()
                && pacemaker.getCreatedAt().isBefore(LocalDateTime.now().minus(STALE_THRESHOLD));
        if (stale) {
            log.error("고아 페이스메이커 감지 → FALLBACK 전환 - pacemakerId={}, status={}, createdAt={}. "
                            + "LLM 파이프라인에서 유실된 작업이므로 원인 확인 필요 (강제 종료·크래시 등)",
                    pacemaker.getId(), pacemaker.getStatus(), pacemaker.getCreatedAt());
            pacemaker.fallback();
        }
    }

}
