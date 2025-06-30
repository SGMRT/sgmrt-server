package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class SoloRunDetailInfo extends RunDetailInfo {

    private CourseInfo courseInfo;
    private RunRecordInfo recordInfo;

    // TODO : 공개 설정 필드가 생기면 공개 설정 유무로 바꾸기
    @QueryProjection
    public SoloRunDetailInfo(Long startedAt, String runningName, CourseInfo courseInfo, RunRecordInfo recordInfo, String telemetryUrl) {
        super(startedAt, runningName, telemetryUrl);
        this.courseInfo = courseInfo.getName() == null ? null : courseInfo;
        this.recordInfo = recordInfo;
    }
}
