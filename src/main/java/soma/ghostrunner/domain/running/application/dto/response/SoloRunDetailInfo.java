package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class SoloRunDetailInfo extends RunDetailInfo {

    private CourseInfo courseInfo;
    private RunRecordInfo recordInfo;

    @QueryProjection
    public SoloRunDetailInfo(Long startedAt, String runningName,
                             CourseInfo courseInfo, RunRecordInfo recordInfo,
                             String telemetryUrl, Boolean isPublic) {
        super(startedAt, runningName, telemetryUrl, isPublic);
        this.courseInfo = courseInfo;
        this.recordInfo = recordInfo;
    }

}
