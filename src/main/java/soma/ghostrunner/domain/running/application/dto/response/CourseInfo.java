package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class CourseInfo {

    private Long id;
    private String name;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isPublic;

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic) {
        this.id = id;
        this.name = name;
        this.isPublic = isPublic;
    }

    @QueryProjection
    public CourseInfo(Long id, String name) {
        this.id = id;
        this.name = name;
    }

}
