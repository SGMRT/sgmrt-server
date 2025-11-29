package soma.ghostrunner.domain.running.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class TelemetryCalculationException extends BusinessException {

    public TelemetryCalculationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
