package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

// TODO : DTO 구조 개선
@Getter
public class GhostRunDetailInfo extends RunDetailInfo {

    private CourseInfo courseInfo;
    private MemberAndRunRecordInfo myRunInfo;
    @JsonIgnore
    private Long ghostRunId;
    private MemberAndRunRecordInfo ghostRunInfo;
    private RunComparisonInfo comparisonInfo;

    @QueryProjection
    public GhostRunDetailInfo(Long startedAt, String runningName,
                              CourseInfo courseInfo, MemberAndRunRecordInfo myRunInfo,
                              Long ghostRunId, String telemetryUrl, Boolean isPublic) {
        super(startedAt, runningName, telemetryUrl, isPublic);
        this.courseInfo = courseInfo;
        this.ghostRunId = ghostRunId;
        this.myRunInfo = myRunInfo;
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
