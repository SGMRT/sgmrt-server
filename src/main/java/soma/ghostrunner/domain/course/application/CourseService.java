package soma.ghostrunner.domain.course;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.global.common.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    public Long save(Course course) {
        return courseRepository.save(course).getId();
    }

    public Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, id));
    }
}
