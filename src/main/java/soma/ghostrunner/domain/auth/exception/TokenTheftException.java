package soma.ghostrunner.domain.auth.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.AuthException;

public class TokenTheftException extends AuthException {

    public TokenTheftException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
