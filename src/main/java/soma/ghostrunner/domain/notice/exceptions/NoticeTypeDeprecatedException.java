package soma.ghostrunner.domain.notice.exceptions;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class NoticeTypeDeprecatedException extends BusinessException {

  public NoticeTypeDeprecatedException(ErrorCode errorCode) {
    super(errorCode);
  }
  public NoticeTypeDeprecatedException() {super(ErrorCode.DEPRECATED_NOTICE_TYPE);}

}
