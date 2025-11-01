package soma.ghostrunner.domain.notification.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class ExpoDeviceNotRegisteredException extends BusinessException {

    public ExpoDeviceNotRegisteredException(String message) {
        super(ErrorCode.EXPO_DEVICE_NOT_REGISTERED, message);
    }

    public ExpoDeviceNotRegisteredException() {
      super(ErrorCode.EXPO_DEVICE_NOT_REGISTERED);
    }

}
