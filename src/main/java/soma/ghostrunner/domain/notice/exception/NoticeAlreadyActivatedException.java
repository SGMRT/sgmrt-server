package soma.ghostrunner.domain.notice.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class NoticeAlreadyActivatedException extends BusinessException {

  public NoticeAlreadyActivatedException() {
    super(ErrorCode.NOTICE_ALREADY_ACTIVATED);
  }

  public NoticeAlreadyActivatedException(String message) {
      super(ErrorCode.NOTICE_ALREADY_ACTIVATED, message);
  }

}
