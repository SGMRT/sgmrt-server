package soma.ghostrunner.domain.notice.exceptions;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class NoticeNotFoundException extends BusinessException {
    public NoticeNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NoticeNotFoundException() {super(ErrorCode.ENTITY_NOT_FOUND);}
}
