package soma.ghostrunner.domain.course.dao;

import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;

import java.util.List;
import java.util.Optional;

public interface CustomCourseRepository {

  Optional<CourseDetailedResponse> findCourseDetailedById(Long courseId);

  List<Course> findCoursesWithFilters(Double minLat, Double maxLat, Double minLng, Double maxLng, CourseSearchFilterDto filters);

}
