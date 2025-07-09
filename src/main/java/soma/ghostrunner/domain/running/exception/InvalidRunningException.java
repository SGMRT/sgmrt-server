package soma.ghostrunner.domain.running.exception;

import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.InvalidValueException;

public class InvalidRunningException extends InvalidValueException {

    public InvalidRunningException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidRunningException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
