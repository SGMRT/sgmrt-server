package soma.ghostrunner.domain.course.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class CourseAccessDeniedException extends BusinessException {

    public CourseAccessDeniedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

}
