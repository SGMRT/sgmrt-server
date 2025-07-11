package soma.ghostrunner.domain.course.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.course.application.CourseFacade;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.course.dto.response.CourseRankingResponse;
import soma.ghostrunner.domain.course.dto.response.CourseResponse;

import java.util.List;

import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/courses")
public class CourseApi {

    private final CourseFacade courseFacade;

    @GetMapping
    public List<CourseResponse> getCourses(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "2000") @Max(value = 20000) Integer radiusM,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Integer minDistanceM,
            @RequestParam(required = false) Integer maxDistanceM,
            @RequestParam(required = false) Integer minElevationM,
            @RequestParam(required = false) Integer maxElevationM) {
        return courseFacade.findCourses(lat, lng, radiusM,
                minDistanceM, maxDistanceM, minElevationM, maxElevationM, ownerId);
    }

    @GetMapping("/{courseId}")
    public CourseDetailedResponse getCourse(
        @PathVariable("courseId") Long courseId) {
        return courseFacade.findCourse(courseId);
    }

    @PatchMapping("/{courseId}")
    public void updateCourse(
            @PathVariable("courseId") Long courseId,
            @RequestBody CoursePatchRequest request) {
        courseFacade.updateCourse(courseId, request);
    }

    @DeleteMapping("/{courseId}")
    public void deleteCourse(
            @PathVariable("courseId") Long courseId) {
        courseFacade.deleteCourse(courseId);
    }

    @GetMapping("/{courseId}/ghosts")
    public PagedModel<CourseGhostResponse> getGhosts(
            @PathVariable("courseId") Long courseId,
            @PageableDefault(sort = "runningRecord.averagePace") Pageable pageable) {
        return new PagedModel<>(courseFacade.findPublicGhosts(courseId, pageable));
        // max 페이지 크기 설정
    }

    @GetMapping("/{courseId}/ranking")
    public CourseRankingResponse getCourseRanking(
            @PathVariable("courseId") Long courseId,
            @RequestParam Long userId) {
        return courseFacade.findCourseRankingDetail(courseId, userId);
    }

    @GetMapping("/{courseId}/top-ranking")
    public List<CourseGhostResponse> getTopRankingGhosts(
        @PathVariable("courseId") Long courseId,
        @RequestParam(required = false, defaultValue = "10") @Min(value = 1) @Max(value = 50) Integer count) {
        return courseFacade.findTopRankingGhosts(courseId, count);
    }

    @GetMapping("/...")
    public List<TelemetryDto> findCourseTelemetry() {
        return null;
    }

}
