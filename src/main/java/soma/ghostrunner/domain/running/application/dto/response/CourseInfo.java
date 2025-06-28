package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class CourseInfo {

    private Long id;
    private String name;
    private Long runnersCount;

    @QueryProjection
    public CourseInfo(Long id, String name, Long runnersCount) {
        this.id = id;
        this.name = name;
        this.runnersCount = runnersCount;
    }
}
