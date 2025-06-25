package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.common.error.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningQueryService {

    private final RunningRepository runningRepository;

    public Running findRunningById(Long id) {
        return runningRepository.findById(id)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, id));
    }

    public Running findRunningBuRunningIdAndMemberId(Long runningId, Long memberId) {
        return runningRepository.findByRunningIdAndMemberId(runningId, memberId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, "러닝 ID : " + runningId + ", 멤버 ID : " + memberId + "에 해당하는 엔티티를 찾을 수 없습니다."));
    }

}
