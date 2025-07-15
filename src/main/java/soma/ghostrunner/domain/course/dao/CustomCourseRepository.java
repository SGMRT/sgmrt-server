package soma.ghostrunner.domain.course.dao;

import java.util.List;
import java.util.Optional;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;

public interface CustomCourseRepository {
  Optional<CourseDetailedResponse> findCourseDetailedById(Long courseId);

  List<Course> findCoursesWithFilters(Double minLat, Double maxLat,
      Double minLng, Double maxLng, Integer minDistanceM, Integer maxDistanceM,
      Integer minElevationM, Integer maxElevationM, Long ownerId);

}
