package soma.ghostrunner.domain.course.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.dto.response.CourseResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/courses")
public class CourseApi {

    private final CourseService courseService;

    @GetMapping
    public List<CourseResponse> getCourses(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "100") Integer radiusKm,
            @RequestParam(required = false) Long ownerId) {
        return courseService.searchCourses(lat, lng, radiusKm, ownerId);
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<Void> patchCourseName(
            @PathVariable("courseId") Long courseId,
            @RequestBody CoursePatchRequest request) {
        courseService.updateCourseName(courseId, request.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable("courseId") Long courseId
    ) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.ok().build();
    }

}
