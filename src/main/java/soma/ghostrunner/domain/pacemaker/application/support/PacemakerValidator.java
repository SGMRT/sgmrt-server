package soma.ghostrunner.domain.pacemaker.application.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.application.CourseReader;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;

/**
 * Pacemaker 생성 요청에 대한 검증을 담당하는 Validato
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PacemakerValidator {

    private final CourseReader courseReader;

    /**
     * Pacemaker 생성 요청 검증
     * 현재는 코스 존재 여부만 확인
     *
     * @param courseId 코스 ID
     */
    public void validateCreationRequest(Long courseId) {
        validateCourseExists(courseId);
    }

    private void validateCourseExists(Long courseId) {
        try {
            courseReader.findCourseById(courseId);
        } catch (CourseNotFoundException e) {
            log.warn("Pacemaker 생성 실패: 코스 존재하지 않음. courseId={}", courseId);
            throw e;
        }
    }

}
