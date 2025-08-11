package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

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
    private Integer totalElevation;

    @QueryProjection
    public RunRecordInfo(Double distance, Long duration, Integer cadence, Integer bpm, Integer calories,
                         Double averagePace, Double highestPace, Double lowestPace, Double elevationGain, Double elevationLoss) {
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
        this.totalElevation = (int) Math.round(elevationGain + elevationLoss);
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

}
