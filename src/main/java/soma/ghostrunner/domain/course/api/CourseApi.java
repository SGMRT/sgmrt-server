package soma.ghostrunner.domain.course.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
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
            @RequestParam(required = false, defaultValue = "5") Integer radiusKm,
            @RequestParam(required = false) Long ownerId) {
        return courseService.searchCourses(lat, lng, radiusKm, ownerId);
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
    public Page<Object> getGhosts(
            @PathVariable("courseId") Long courseId,
            @PageableDefault(sort = "pace", direction = Direction.DESC) Pageable pageable
    ) {
        return runningQueryService.findPublicGhostRunsByCourseId(courseId, pageable);
    }
}
