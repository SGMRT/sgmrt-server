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

    @NotEmpty @Column(name = "distance_km")
    private double distance;

    @NotEmpty @Column(name = "elevation_gain_m")
    private int elevationGain;

    @NotEmpty @Column(name = "elevation_loss_m")
    private int elevationLoss;

    @NotEmpty @Column(name = "average_pace_min/km")
    private double averagePace;

    @NotEmpty @Column(name = "highest_pace_min/km")
    private double highestPace;

    @NotEmpty @Column(name = "lowest_pace_min/km")
    private double lowestPace;

    @NotEmpty @Column(name = "duration_sec")
    private long duration;

    @NotEmpty @Column(name = "burned_calories_kcal")
    private int burnedCalories;

    @NotEmpty @Column(name = "average_cadence_spm")
    private int cadence;

    @NotEmpty @Column(name = "average_bpm")
    private int bpm;

    @Builder
    private RunningRecord(double distance, int elevationGain, int elevationLoss, double averagePace,
                          double highestPace, double lowestPace, long duration, int burnedCalories, int cadence, int bpm) {
        this.distance = distance;
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

    public static RunningRecord of(double distance, int elevationGain, int elevationLoss, double averagePace,
                                   double highestPace, double lowestPace, long duration, int burnedCalories, int cadence, int bpm) {
        return RunningRecord.builder()
                .distance(distance)
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
