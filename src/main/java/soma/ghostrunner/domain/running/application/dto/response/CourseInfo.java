package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import soma.ghostrunner.domain.course.domain.Course;

@Getter
public class CourseInfo {
    private Long id;
    private String name;
    private Long runnersCount;

    @QueryProjection
    public CourseInfo(Course course, Long runnersCount) {
        this.id = course.getId();
        this.name = course.getName();
        this.runnersCount = runnersCount;
    }
}
