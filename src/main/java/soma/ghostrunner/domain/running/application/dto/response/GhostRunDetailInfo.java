package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.Setter;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class GhostRunDetailInfo {

    private Long startedAt;
    private String runningName;
    private CourseInfo courseInfo;
    private MemberAndRunRecordInfo myRunInfo;
    private GhostMemberAndRunRecordInfo ghostRunInfo;
    private RunComparisonInfo comparisonInfo;
    @JsonIgnore
    private String telemetryUrl;
    @Setter
    private TelemetryDto telemetries;

    @QueryProjection
    public GhostRunDetailInfo(Long startedAt, String runningName, CourseInfo courseInfo, MemberAndRunRecordInfo myRunInfo, String telemetryUrl) {
        this.startedAt = startedAt;
        this.runningName = runningName;
        this.courseInfo = courseInfo;
        this.myRunInfo = myRunInfo;
        this.telemetryUrl = telemetryUrl;
    }

    public void setGhostRunInfo(MemberAndRunRecordInfo ghostRunInfo) {
        this.ghostRunInfo = new GhostMemberAndRunRecordInfo(ghostRunInfo);
        this.comparisonInfo = new RunComparisonInfo(myRunInfo.getRecordInfo(), ghostRunInfo.getRecordInfo());
    }

    @Getter
    public class GhostMemberAndRunRecordInfo {
        private String nickname;
        private String profileUrl;
        private Long duration;
        private Integer cadence;
        private Double pace;

        public GhostMemberAndRunRecordInfo(MemberAndRunRecordInfo ghostInfo) {
            this.nickname = ghostInfo.getNickname();
            this.profileUrl = ghostInfo.getProfileUrl();
            this.duration = ghostInfo.getRecordInfo().getDuration();
            this.cadence = ghostInfo.getRecordInfo().getCadence();
            this.pace = ghostInfo.getRecordInfo().getAveragePace();
        }
    }

    @Getter
    public class RunComparisonInfo {
        private Double distance;
        private Long duration;
        private Integer cadence;
        private Double pace;

        public RunComparisonInfo(RunRecordInfo myRecord, RunRecordInfo ghostRecord) {
            this.distance = calculateDistanceDiff(myRecord, ghostRecord);
            this.duration = myRecord.getDuration() - ghostRecord.getDuration();
            this.cadence = myRecord.getCadence() - ghostRecord.getCadence();
            this.pace = calculatePaceDiff(myRecord.getAveragePace(), ghostRecord.getAveragePace());
        }

        // 거리차 계산
        private Double calculateDistanceDiff(RunRecordInfo myRecord, RunRecordInfo ghostRecord) {
            BigDecimal myDist = BigDecimal.valueOf(myRecord.getDistance());
            BigDecimal myPace = BigDecimal.valueOf(myRecord.getAveragePace());
            BigDecimal ghostPace = BigDecimal.valueOf(ghostRecord.getAveragePace());

            BigDecimal paceRatio = myPace.divide(ghostPace, 2 , RoundingMode.HALF_UP);
            BigDecimal distanceLost = myDist.multiply(paceRatio);
            BigDecimal resultDistanceDiff = myDist.subtract(distanceLost);
            return resultDistanceDiff.doubleValue();
        }

        // 페이스 계산
        private Double calculatePaceDiff(Double myPace, Double ghostPace) {
            BigDecimal myBigDecimalRecord = BigDecimal.valueOf(myPace);
            BigDecimal ghostBigDecimalRecord = BigDecimal.valueOf(ghostPace);
            return myBigDecimalRecord.subtract(ghostBigDecimalRecord).doubleValue();
        }
    }
}
