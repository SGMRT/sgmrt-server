package soma.ghostrunner.domain.running.exception;

import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.InvalidValueException;

public class InvalidGhostRunningException extends InvalidValueException {
    public InvalidGhostRunningException(ErrorCode errorCode) {
        super(errorCode);
    }
}
