package soma.ghostrunner.domain.running.exception;

import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.EntityNotFoundException;

public class RunningNotFoundException extends EntityNotFoundException {

    public RunningNotFoundException(ErrorCode errorCode, long runningId) {
        super(errorCode, runningId);
    }

    public RunningNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
