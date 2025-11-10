package soma.ghostrunner.domain.notification.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class PushHistoryNotFound extends BusinessException {

    public PushHistoryNotFound(String message) {
        super(ErrorCode.ENTITY_NOT_FOUND, message);
    }

    public PushHistoryNotFound() {
        super(ErrorCode.ENTITY_NOT_FOUND);
    }
}
