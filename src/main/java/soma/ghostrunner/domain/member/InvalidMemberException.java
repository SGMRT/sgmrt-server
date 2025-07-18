package soma.ghostrunner.domain.member;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.InvalidValueException;

public class InvalidMemberException extends InvalidValueException {

    public InvalidMemberException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
