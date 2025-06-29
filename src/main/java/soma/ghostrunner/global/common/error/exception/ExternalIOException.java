package soma.ghostrunner.global.common.error.exception;

import soma.ghostrunner.global.common.error.ErrorCode;

public class ExternalIOException extends BusinessException {

    public ExternalIOException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ExternalIOException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
