package soma.ghostrunner.global.error.exception;

import soma.ghostrunner.global.error.ErrorCode;

public class InvalidValueException extends BusinessException {

    public InvalidValueException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidValueException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
