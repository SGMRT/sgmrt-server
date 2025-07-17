package soma.ghostrunner.global.security.exception;

import soma.ghostrunner.global.error.exception.AuthException;

public class ParsingTokenException extends AuthException {

    public ParsingTokenException(String message) {
        super(message);
    }

}
