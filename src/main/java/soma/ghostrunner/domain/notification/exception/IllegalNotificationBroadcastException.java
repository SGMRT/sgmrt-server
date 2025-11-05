package soma.ghostrunner.domain.notification.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class IllegalNotificationBroadcastException extends BusinessException {
    public IllegalNotificationBroadcastException() {
        super(ErrorCode.ILLEGAL_NOTIFICATION_BROADCAST);
    }

    public IllegalNotificationBroadcastException(ErrorCode errorCode) {
        super(errorCode);
    }

}
