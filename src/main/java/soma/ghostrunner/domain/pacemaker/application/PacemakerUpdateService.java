package soma.ghostrunner.domain.pacemaker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;

/**
 * 페이스메이커 업데이트/삭제 담당 서비스
 * - 러닝 후 상태 업데이트
 * - 페이스메이커 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacemakerUpdateService {

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerQueryService queryService;

    /**
     * 러닝 완료 후 페이스메이커 상태 업데이트
     */
    @Transactional
    public void updateAfterRunning(String memberUuid, Long pacemakerId, Long runningId) {
        Pacemaker pacemaker = queryService.findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);
        pacemaker.updateAfterRunning(runningId);
    }

    /**
     * 페이스메이커 삭제 (소프트 삭제)
     */
    @Transactional
    public void deletePacemaker(String memberUuid, Long pacemakerId) {
        Pacemaker pacemaker = queryService.findPacemaker(pacemakerId);
        pacemaker.verifyMember(memberUuid);
        deletePacemakers(pacemakerId);
    }

    private void deletePacemakers(Long pacemakerId) {
        pacemakerRepository.softDelete(pacemakerId);
        pacemakerRepository.softDeleteAllByPacemakerId(pacemakerId);
    }

}
