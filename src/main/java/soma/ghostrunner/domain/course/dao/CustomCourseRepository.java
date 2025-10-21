package soma.ghostrunner.domain.course.dao;

import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.enums.CourseSortType;

import java.util.List;

public interface CustomCourseRepository {

  List<Course> findCoursesWithFilters(Double curLat, Double curLng,
                                      Double minLat, Double maxLat, Double minLng, Double maxLng, 
                                      CourseSearchFilterDto filters, CourseSortType sort, String viewerUuid);

  List<Long> findCourseIdsWithFilters(Double curLat, Double curLng,
                                      Double minLat, Double maxLat, Double minLng, Double maxLng,
                                      CourseSearchFilterDto filters, CourseSortType sort, String viewerUuid);

}
