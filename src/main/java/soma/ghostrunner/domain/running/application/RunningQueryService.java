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

}
