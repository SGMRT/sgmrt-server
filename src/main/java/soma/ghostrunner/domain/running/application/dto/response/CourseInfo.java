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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long runnersCount;

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic, Long runnersCount) {
        this.id = id;
        this.name = name;
        this.isPublic = isPublic;
        this.runnersCount = runnersCount;
    }

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic) {
        if (isPublic) {
            this.id = id;
            this.name = name;
        }
    }

}
