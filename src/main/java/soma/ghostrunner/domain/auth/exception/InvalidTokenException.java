package soma.ghostrunner.domain.auth.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.AuthException;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
