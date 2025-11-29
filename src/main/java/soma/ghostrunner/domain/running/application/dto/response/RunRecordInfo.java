package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningRecord;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private Integer elevationAverage;

    @QueryProjection
    public RunRecordInfo(Double distance, Long duration, Integer cadence,
                         Integer bpm, Integer calories, Double averagePace, Double highestPace, Double lowestPace,
                         Double elevationGain, Double elevationLoss, Double elevationAverage) {
        this.distance = distance;
        this.duration = duration;
        this.cadence = cadence;
        this.bpm = bpm;
        this.calories = calories;
        this.averagePace = averagePace;
        this.highestPace = highestPace;
        this.lowestPace = lowestPace;
        this.elevationGain = (int) Math.round(elevationGain);
        this.elevationLoss = (int) Math.round(elevationLoss);
        this.elevationAverage = (int) Math.round(elevationAverage);
    }

    @QueryProjection
    public RunRecordInfo(Long duration, Integer cadence, Double averagePace) {
        this.duration = duration;
        this.cadence = cadence;
        this.averagePace = averagePace;
    }

    @QueryProjection
    public RunRecordInfo(Double distance, Long duration, Double averagePace, Integer cadence) {
        this.distance = distance;
        this.duration = duration;
        this.averagePace = averagePace;
        this.cadence = cadence;
    }

    public RunRecordInfo(RunningRecord runningRecord) {
        this.distance = runningRecord.getDistance();
        this.duration = runningRecord.getDuration();
        this.cadence = runningRecord.getCadence();
        this.bpm = runningRecord.getBpm();
        this.calories = runningRecord.getBurnedCalories();
        this.averagePace = runningRecord.getAveragePace();
        this.highestPace = runningRecord.getHighestPace();
        this.lowestPace = runningRecord.getLowestPace();
        this.elevationGain = (int) Math.round(runningRecord.getElevationGain());
        this.elevationLoss = (int) Math.round(runningRecord.getElevationLoss());
        this.elevationAverage = (int) Math.round(runningRecord.getElevationAverage());
    }

}
