package soma.ghostrunner.global.common.error.exception;

import soma.ghostrunner.global.common.error.ErrorCode;

public class ParsingException extends BusinessException {
    public ParsingException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ParsingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
