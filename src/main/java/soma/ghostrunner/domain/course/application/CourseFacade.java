package soma.ghostrunner.domain.course.application;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.course.dto.response.CourseRankingResponse;
import soma.ghostrunner.domain.course.dto.response.CourseResponse;
import soma.ghostrunner.domain.running.application.RunningQueryService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseFacade {
    private final CourseService courseService;
    private final RunningQueryService runningQueryService;

    public List<CourseResponse> searchCourses(
            Double lat, Double lng, Integer radiusM,
            Integer minDistanceM, Integer maxDistanceM,
            Integer minElevationM, Integer maxElevationM,
            Long ownerId) {
        return courseService.searchCourses(lat, lng, radiusM,
                minDistanceM, maxDistanceM, minElevationM, maxElevationM, ownerId);
    }

    public CourseDetailedResponse getCourse(Long courseId) {
        // needs refactoring
        return courseService.getCourse(courseId);
    }

    public void updateCourse(Long courseId, CoursePatchRequest request) {
        courseService.updateCourse(courseId, request);
    }

    public void deleteCourse(Long courseId) {
        courseService.deleteCourse(courseId);
    }

    public Page<CourseGhostResponse> findPublicGhosts(Long courseId, Pageable pageable) {
        return runningQueryService.findPublicGhostRunsByCourseId(courseId, pageable);
    }

    public CourseRankingResponse getCourseRanking(Long courseId, Long userId) {
        return runningQueryService.findUserRankingInCourse(courseId, userId);
    }

    public List<CourseGhostResponse> getCourseTopRanking(Long courseId, int count) {
        Page<CourseGhostResponse> rankedGhostsPage = runningQueryService.findPublicGhostRunsByCourseId(
                courseId,
                PageRequest.of(0, count, Sort.by(Sort.Direction.ASC, "runningRecord.averagePace"))
        );
        return rankedGhostsPage.getContent();
    }


}
