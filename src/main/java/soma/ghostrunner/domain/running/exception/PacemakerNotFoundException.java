package soma.ghostrunner.domain.running.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.EntityNotFoundException;

public class PacemakerNotFoundException extends EntityNotFoundException {

    public PacemakerNotFoundException(ErrorCode errorCode, long pacemakerId) {
        super(errorCode, pacemakerId);
    }

    public PacemakerNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
