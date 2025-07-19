package soma.ghostrunner.global.error.exception;

import soma.ghostrunner.global.error.ErrorCode;

public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
