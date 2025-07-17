package soma.ghostrunner.global.security.exception;

import soma.ghostrunner.global.error.exception.AuthException;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException(String message) {
        super(message);
    }

}
