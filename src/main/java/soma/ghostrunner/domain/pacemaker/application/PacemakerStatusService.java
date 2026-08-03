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

}
