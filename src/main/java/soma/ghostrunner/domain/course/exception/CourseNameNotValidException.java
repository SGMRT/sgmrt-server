package soma.ghostrunner.domain.course.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class CourseNameNotValidException extends BusinessException {
    public CourseNameNotValidException(ErrorCode errorCode) {
        super(errorCode, "invalid course name");
    }
    public CourseNameNotValidException(ErrorCode errorCode, String message) {super(errorCode, message);}
}
