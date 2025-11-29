package soma.ghostrunner.domain.running.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.EntityNotFoundException;

public class RunningNotFoundException extends EntityNotFoundException {

    public RunningNotFoundException(ErrorCode errorCode, long runningId) {
        super(errorCode, runningId);
    }

    public RunningNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RunningNotFoundException() {
        super(ErrorCode.ENTITY_NOT_FOUND);
    }

}
