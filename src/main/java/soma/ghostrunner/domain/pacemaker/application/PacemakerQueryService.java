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

import java.util.List;

/**
 * 페이스메이커 조회 전용 서비스
 * - 페이스메이커 단건 조회
 * - 코스 내 페이스메이커 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PacemakerQueryService {

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final PacemakerApplicationMapper mapper;
    private final RunningTipsProvider runningTipsProvider;

    /**
     * 페이스메이커 단건 조회 (폴링용)
     */
    public PacemakerPollingResponse getPacemaker(Long pacemakerId, String memberUuid) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);

        if (pacemaker.isNotCompleted()) {
            return mapper.toPacemakerPollingResponse(pacemaker);
        }

        List<PacemakerSet> pacemakerSets = pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemakerId);
        return mapper.toPacemakerPollingResponse(pacemaker, pacemakerSets, runningTipsProvider.getRandomTip());
    }

    /**
     * 코스 내 페이스메이커 조회 (폴링용)
     */
    public PacemakerInCourseViewPollingResponse getPacemakerInCourse(String memberUuid, Long courseId) {
        Pacemaker pacemaker = findPacemakerInCourse(memberUuid, courseId);
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

}
