package soma.ghostrunner.domain.course.api;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.course.dto.response.CourseResponse;

import java.util.List;
import soma.ghostrunner.domain.running.application.RunningQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/courses")
public class CourseApi {

    private final CourseService courseService;
    private final RunningQueryService runningQueryService;

    @GetMapping
    public List<CourseResponse> getCourses(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "5000") Integer radiusM,
            @RequestParam(required = false) Long ownerId) {
        return courseService.searchCourses(lat, lng, radiusM, ownerId);
    }

    @PatchMapping("/{courseId}")
    public void updateCourse(
            @PathVariable("courseId") Long courseId,
            @RequestBody CoursePatchRequest request) {
        courseService.updateCourse(courseId, request);
    }

    @DeleteMapping("/{courseId}")
    public void deleteCourse(
            @PathVariable("courseId") Long courseId) {
        courseService.deleteCourse(courseId);
    }

    @GetMapping("/{courseId}/ghosts")
    public Page<CourseGhostResponse> getGhosts(
            @PathVariable("courseId") Long courseId,
            @PageableDefault(sort = "runningRecord.averagePace", direction = Direction.ASC) Pageable pageable) {
        return runningQueryService.findPublicGhostRunsByCourseId(courseId, pageable);
    }

    @GetMapping("/{courseId}/ranking")
    public Integer getCourseRanking(
            @PathVariable("courseId") Long courseId,
            @RequestParam Long userId) {
        return runningQueryService.findRankingOfUserInCourse(courseId, userId);
    }

    @GetMapping("/{courseId}/top-ranking")
    public List<CourseGhostResponse> getCourseTopRanking(
        @PathVariable("courseId") Long courseId,
        @RequestParam(required = false, defaultValue = "10") @Min(value = 1) Integer count) {
        Page<CourseGhostResponse> rankedGhostsPage = runningQueryService.findPublicGhostRunsByCourseId(
            courseId,
            PageRequest.of(0, count, Sort.by(Direction.ASC, "runningRecord.averagePace"))
        );
        return rankedGhostsPage.getContent();
    }

}
