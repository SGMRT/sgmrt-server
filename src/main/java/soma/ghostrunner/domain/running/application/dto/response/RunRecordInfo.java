package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
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
                         Double averagePace, Double highestPace, Double lowestPace, Integer elevationGain, Integer elevationLoss) {
        this.distance = distance;
        this.duration = duration;
        this.cadence = cadence;
        this.bpm = bpm;
        this.calories = calories;
        this.averagePace = averagePace;
        this.highestPace = highestPace;
        this.lowestPace = lowestPace;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
        this.totalElevation = elevationGain + elevationLoss;
    }
}
