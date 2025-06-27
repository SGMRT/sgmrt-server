package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.RunningRecord;

@Data
@NoArgsConstructor
public class RunRecordInfo {
    private Double distance;
    private Long duration;
    private Integer cadence;
    private Integer bpm;
    private Integer calories;
    private Double averagePace;
    private Double highestPace;
    private Double lowestPace;
    private Integer elevationGain;
    private Integer elevationLoss;
    private Integer totalElevation;

    @QueryProjection
    public RunRecordInfo(RunningRecord runningRecord) {
        this.distance = runningRecord.getDistance();
        this.duration = runningRecord.getDuration();
        this.cadence = runningRecord.getCadence();
        this.bpm = runningRecord.getBpm();
        this.calories = runningRecord.getBurnedCalories();
        this.averagePace = runningRecord.getAveragePace();
        this.highestPace = runningRecord.getHighestPace();
        this.lowestPace = runningRecord.getLowestPace();
        this.elevationGain = runningRecord.getElevationGain();
        this.elevationLoss = runningRecord.getElevationLoss();
        this.totalElevation = runningRecord.getElevationGain() + runningRecord.getElevationLoss();
    }
}
