package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerInCourseViewPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerApplicationMapper;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.formula.RunningTipsProvider;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.time.Duration;
import java.util.List;

/**
 * 페이스메이커 조회 전용 서비스
 * - 페이스메이커 단건 조회 / 코스 내 조회 (폴링용)
 * - 남은 Rate Limit 조회
 * - 폴링 조회는 지연 판정을 겸한다 — 임계치를 넘긴 고아 레코드를 FALLBACK(FAILED)으로 전환해
 *   Rule-Base 훈련표가 응답되도록 한다. 이 쓰기 때문에 폴링 메서드는 readOnly를 해제한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PacemakerQueryService {

    /**
     * 임계치는 graceful shutdown 대기(20분)·LLM 재시도 워스트(~18분)보다 길어야
     * 정상 진행 중인 작업을 고아로 오판하지 않는다.
     */
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(30);

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final PacemakerApplicationMapper mapper;
    private final RunningTipsProvider runningTipsProvider;
    private final PacemakerRateLimitService rateLimitService;

    /**
     * 페이스메이커 단건 조회 (폴링용)
     * - 지연 판정의 상태 전환이 커밋되어야 하므로 readOnly를 해제한다
     */
    @Transactional
    public PacemakerPollingResponse getPacemaker(Long pacemakerId, String memberUuid) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);
        fallbackIfStale(pacemaker);

        if (pacemaker.isNotCompleted()) {
            return mapper.toPacemakerPollingResponse(pacemaker);
        }

        List<PacemakerSet> pacemakerSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId);
        return mapper.toPacemakerPollingResponse(pacemaker, pacemakerSets, runningTipsProvider.getRandomTip());
    }

    /**
     * 코스 내 페이스메이커 조회 (폴링용)
     * - 지연 판정의 상태 전환이 커밋되어야 하므로 readOnly를 해제한다
     */
    @Transactional
    public PacemakerInCourseViewPollingResponse getPacemakerInCourse(String memberUuid, Long courseId) {
        Pacemaker pacemaker = findPacemakerInCourse(memberUuid, courseId);
        fallbackIfStale(pacemaker);
        if (pacemaker.isNotCompleted()) {
            return mapper.toPacemakerInCourseViewPollingResponse(pacemaker);
        }

        List<PacemakerSet> pacemakerSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemaker.getId());
        return mapper.toPacemakerInCourseViewPollingResponse(pacemaker, pacemakerSets);
    }

    /**
     * 페이스메이커 ID로 조회 (내부용)
     */
    public Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    /**
     * 코스 내 페이스메이커 조회 (내부용)
     */
    public Pacemaker findPacemakerInCourse(String memberUuid, Long courseId) {
        return pacemakerRepository.findByCourseId(courseId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, courseId + "에 대한 페이스메이커를 찾을 수 없음"));
    }

    /**
     * 남은 일일 사용량 조회
     */
    public Long getRateLimitCounter(String memberUuid) {
        return rateLimitService.getRemainingCount(memberUuid);
    }

    /**
     * 지연 판정 — 판정과 전환은 도메인(Pacemaker)이 하고, 고아 발생 알림만 여기서 남긴다.
     * SIGKILL·크래시·TX2 실패 등으로 LLM 작업이 유실된 경우라 발생 자체가 이상 신호다.
     */
    private void fallbackIfStale(Pacemaker pacemaker) {
        if (pacemaker.fallbackIfStaleOver(STALE_THRESHOLD)) {
            log.error("고아 페이스메이커 감지 → FALLBACK 전환 - pacemakerId={}, createdAt={}. "
                            + "LLM 파이프라인에서 유실된 작업이므로 원인 확인 필요 (강제 종료·크래시 등)",
                    pacemaker.getId(), pacemaker.getCreatedAt());
        }
    }

}
