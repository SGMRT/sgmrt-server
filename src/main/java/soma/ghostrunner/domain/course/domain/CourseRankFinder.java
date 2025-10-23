package soma.ghostrunner.domain.course.domain;

import soma.ghostrunner.domain.course.dto.CourseRankInfo;

import java.util.List;

public interface CourseRankFinder {

    List<CourseRankInfo> findCourseTop4RankInfoByCourseId(Long courseId);

    Long countRunnersByCourseId(Long courseId);

    CourseRankInfo findFirstRunnerByCourseId(Long courseId);

}
