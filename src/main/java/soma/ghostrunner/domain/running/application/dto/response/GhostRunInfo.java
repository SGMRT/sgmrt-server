package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.Setter;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

@Getter
public class GhostRunInfo {

    private Long startedAt;
    private String runningName;
    private CourseInfo courseInfo;
    private MemberAndRunRecordInfo myRunInfo;
    private MemberAndRunRecordInfo ghostRunInfo;
    private RunComparisonInfo comparisonInfo;
    @JsonIgnore
    private String telemetryUrl;
    @Setter
    private TelemetryDto telemetries;

    @QueryProjection
    public GhostRunInfo(Long startedAt, String runningName, CourseInfo courseInfo, MemberAndRunRecordInfo myRunInfo, String telemetryUrl) {
        this.startedAt = startedAt;
        this.runningName = runningName;
        this.courseInfo = courseInfo;
        this.myRunInfo = myRunInfo;
        this.telemetryUrl = telemetryUrl;
    }

    public void setGhostRunInfo(MemberAndRunRecordInfo ghostRunInfo) {
        this.ghostRunInfo = ghostRunInfo;
        this.comparisonInfo = new RunComparisonInfo(myRunInfo.getRecordInfo(), ghostRunInfo.getRecordInfo());
    }

    @Getter
    public class RunComparisonInfo {
        private Double distance;
        private Long duration;
        private Integer cadence;
        private Double pace;

        public RunComparisonInfo(RunRecordInfo myRecord, RunRecordInfo ghostRecord) {
            this.distance = myRecord.getDistance() - ((myRecord.getDistance() * myRecord.getAveragePace()) / ghostRecord.getAveragePace());
            this.duration = myRecord.getDuration() - ghostRecord.getDuration();
            this.cadence = myRecord.getCadence() - ghostRecord.getCadence();
            this.pace = myRecord.getAveragePace() - ghostRecord.getAveragePace();
        }
    }
}
