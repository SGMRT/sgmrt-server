package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class CourseInfo {

    private Long id;
    private String name;
    private Boolean isPublic;
    private Double distance;

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic, Double distance) {
        this.id = id;
        this.name = name;
        this.isPublic = isPublic;
        this.distance = distance;
    }


}
