package soma.ghostrunner.domain.course.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import soma.ghostrunner.domain.course.application.CourseService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/v1/courses")
public class CourseApi {

    private final CourseService courseService;

    @GetMapping
    public Object getCourses(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "100") Integer radiusKm,
            @RequestParam(required = false) Long ownerId) {
        return courseService.searchCourses(lat, lng, radiusKm, ownerId);
    }


}
