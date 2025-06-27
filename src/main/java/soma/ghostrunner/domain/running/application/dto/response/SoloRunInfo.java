package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.Setter;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.ArrayList;
import java.util.List;

@Getter
public class SoloRunInfo {
    private Long startedAt;
    private String runningName;
    private CourseInfo courseInfo;
    private RunRecordInfo recordInfo;
    @JsonIgnore
    private String telemetryUrl;
    @Setter
    private List<TelemetryDto> telemetries;

    // TODO : 공개 설정 필드가 생기면 공개 설정 유무로 바꾸기
    @QueryProjection
    public SoloRunInfo(Running running, CourseInfo courseInfo) {
        this.startedAt = running.getStartedAt();
        this.runningName = running.getRunningName();
        this.courseInfo = courseInfo.getName() == null ? null : courseInfo;
        this.recordInfo = new RunRecordInfo(running.getRunningRecord());
        this.telemetryUrl = running.getTelemetryUrl();
        List<TelemetryDto> telemetries = new ArrayList<>();
    }
}
