package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRecord {

    @Column(name = "distance_km")
    private Double distance;

    @Column(name = "elevation_average_m")
    private Double elevationAverage;

    @Column(name = "elevation_gain_m")
    private Double elevationGain;

    @Column(name = "elevation_loss_m")
    private Double elevationLoss;

    @Column(name = "average_pace_min/km")
    private Double averagePace;

    @Column(name = "highest_pace_min/km")
    private Double highestPace;

    @Column(name = "lowest_pace_min/km")
    private Double lowestPace;

    @Column(name = "duration_sec")
    private Long duration;

    @Column(name = "burned_calories_kcal")
    private Integer burnedCalories;

    @Column(name = "average_cadence_spm")
    private Integer cadence;

    @Column(name = "average_bpm")
    private Integer bpm;

    @Builder(access = AccessLevel.PRIVATE)
    private RunningRecord(Double distance,
                          Double elevationAverage, Double elevationGain, Double elevationLoss,
                          Double averagePace, Double highestPace, Double lowestPace,
                          Long duration, Integer burnedCalories, Integer cadence, Integer bpm) {
        this.distance = distance;
        this.elevationAverage = elevationAverage;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
        this.averagePace = averagePace;
        this.highestPace = highestPace;
        this.lowestPace = lowestPace;
        this.duration = duration;
        this.burnedCalories = burnedCalories;
        this.cadence = cadence;
        this.bpm = bpm;
    }

    public static RunningRecord of(Double distance,
                                   Double elevationAverage, Double elevationGain, Double elevationLoss,
                                   Double averagePace, Double highestPace, Double lowestPace,
                                   Long duration, Integer burnedCalories, Integer cadence, Integer bpm) {
        return RunningRecord.builder()
                .distance(distance)
                .elevationAverage(elevationAverage)
                .elevationGain(elevationGain)
                .elevationLoss(elevationLoss)
                .averagePace(averagePace)
                .highestPace(highestPace)
                .lowestPace(lowestPace)
                .duration(duration)
                .burnedCalories(burnedCalories)
                .cadence(cadence)
                .bpm(bpm)
                .build();
    }
}
