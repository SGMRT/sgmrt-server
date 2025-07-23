package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class RunInfo {

    private Long runningId;
    private String name;
    private Long startedAt;
    private RunRecordInfo recordInfo;
    private CourseInfo courseInfo;
    private Long ghostRunningId;

    @QueryProjection
    public RunInfo(Long runningId, String name, Long startedAt, RunRecordInfo recordInfo,
                   CourseInfo courseInfo, Long ghostRunningId) {
        this.runningId = runningId;
        this.name = name;
        this.startedAt = startedAt;
        this.recordInfo = recordInfo;
        this.courseInfo = courseInfo;
        this.ghostRunningId = ghostRunningId;
    }

}
