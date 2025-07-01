package soma.ghostrunner.global.common.error.exception;

import soma.ghostrunner.global.common.error.ErrorCode;

public class InvalidValueException extends BusinessException {

    public InvalidValueException(ErrorCode errorCode) {
        super(errorCode);
    }
}
