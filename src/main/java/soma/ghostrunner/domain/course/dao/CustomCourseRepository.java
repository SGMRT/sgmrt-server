package soma.ghostrunner.domain.course.dao;

import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;

import java.util.List;
import java.util.Optional;

public interface CustomCourseRepository {

  List<Course> findCoursesWithFilters(Double curLat, Double curLng, Double minLat, Double maxLat,
                                      Double minLng, Double maxLng, CourseSearchFilterDto filters, CourseSortType sort);

}
