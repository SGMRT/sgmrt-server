package soma.ghostrunner.domain.course.exception;

import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.BusinessException;

public class CourseAlreadyPublicException extends BusinessException {
    public CourseAlreadyPublicException(ErrorCode errorCode) {
        super(errorCode, "Course is already public; cannot be updated");
    }

    public CourseAlreadyPublicException(ErrorCode errorCode, Long courseId) {super(errorCode, "Course id " + courseId + " is already public");}
}
