package soma.ghostrunner.domain.course.dao;

import java.util.Optional;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;

public interface CustomCourseRepository {
  Optional<CourseDetailedResponse> findCourseDetailedById(Long courseId);
}
