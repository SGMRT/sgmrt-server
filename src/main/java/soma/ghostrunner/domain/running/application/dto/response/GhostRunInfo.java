package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

@Data
@NoArgsConstructor
public class GhostRunInfo {
    private Long startedAt;
    private String runningName;
    private CourseInfo courseInfo;
    private MemberAndSoloRunInfo myRunInfo;
    private MemberAndSoloRunInfo ghostRunInfo;
    private RunComparisonInfo comparisonInfo;
    @JsonIgnore
    private String telemetryUrl;
    private TelemetryDto telemetries;

    public GhostRunInfo(Long startedAt, String runningName, CourseInfo courseInfo, MemberAndSoloRunInfo myRunInfo, String telemetryUrl) {
        this.startedAt = startedAt;
        this.runningName = runningName;
        this.courseInfo = courseInfo;
        this.myRunInfo = myRunInfo;
        this.telemetryUrl = telemetryUrl;
    }

    public void setGhostRunInfo(MemberAndSoloRunInfo ghostRunInfo) {
        this.ghostRunInfo = ghostRunInfo;
        this.comparisonInfo = new RunComparisonInfo(myRunInfo.getRecordInfo(), ghostRunInfo.getRecordInfo());
    }
}
