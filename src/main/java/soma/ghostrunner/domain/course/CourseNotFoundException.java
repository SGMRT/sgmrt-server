package soma.ghostrunner.domain.course;

import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.EntityNotFoundException;

public class CourseNotFoundException extends EntityNotFoundException {
    public CourseNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CourseNotFoundException(ErrorCode errorCode, long id) {
        super(errorCode, id);
    }
}
