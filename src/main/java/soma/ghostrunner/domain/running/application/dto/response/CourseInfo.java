package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.domain.support.CoordinateConverter;

import java.util.List;

@Getter
public class CourseInfo {

    private Long id;
    private String name;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isPublic;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long runnersCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CoordinateDto> pathData;

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic, Long runnersCount) {
        this.id = id;
        this.name = name;
        this.isPublic = isPublic;
        this.runnersCount = runnersCount;
    }

    @QueryProjection
    public CourseInfo(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic) {
        if (isPublic) {
            this.id = id;
            this.name = name;
        }
    }

    @QueryProjection
    public CourseInfo(Long id, String name, Boolean isPublic, String pathData) {
        if (isPublic) {
            this.id = id;
            this.name = name;
            this.pathData = CoordinateConverter.convertToCoordinateList(pathData);
        }
    }

}
