package soma.ghostrunner.domain.course.dao;

import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseMetaInfoDto;
import soma.ghostrunner.domain.course.dto.CourseRankInfo;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.enums.CourseSortType;

import java.util.List;
import java.util.Set;

public interface CustomCourseRepository {

  List<Course> findCoursesWithFilters(Double curLat, Double curLng, Double minLat, Double maxLat,
                                      Double minLng, Double maxLng, CourseSearchFilterDto filters, CourseSortType sort);

  List<CourseMetaInfoDto> findCourseMetaInfoByCourseId(Set<Long> courseIds);

}
