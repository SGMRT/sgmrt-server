package soma.ghostrunner.domain.course.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.EntityNotFoundException;

public class CourseNotFoundException extends EntityNotFoundException {
    public CourseNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CourseNotFoundException(ErrorCode errorCode, long id) {
        super(errorCode, id);
    }
}
