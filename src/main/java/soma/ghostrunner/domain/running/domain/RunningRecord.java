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

    @NotEmpty @Column(name = "total_distance")
    private double totalDistance;

    @NotEmpty @Column(name = "total_altitude")
    private double totalAltitude;

    @NotEmpty @Column(name = "average_pace")
    private double avgPace;

    @NotEmpty @Column(name = "total_duration_sec")
    private long totalDuration;

    @NotEmpty @Column(name = "average_calories")
    private int avgCalories;

    @NotEmpty @Column(name = "average_cadence")
    private int avgCadence;

    @NotEmpty @Column(name = "average_bpm")
    private int avgBpm;

    @Builder
    private RunningRecord(double totalDistance, double totalAltitude, double avgPace, long totalDuration, int avgCalories, int avgCadence, int avgBpm) {
        this.totalDistance = totalDistance;
        this.totalAltitude = totalAltitude;
        this.avgPace = avgPace;
        this.totalDuration = totalDuration;
        this.avgCalories = avgCalories;
        this.avgCadence = avgCadence;
        this.avgBpm = avgBpm;
    }

    public static RunningRecord of(double totalDistance, double totalAltitude, double avgPace,
                                   long totalDuration, int avgCalories, int avgCadence, int avgBpm) {
        return RunningRecord.builder()
                .totalDistance(totalDistance)
                .totalAltitude(totalAltitude)
                .avgPace(avgPace)
                .totalDuration(totalDuration)
                .avgCalories(avgCalories)
                .avgCadence(avgCadence)
                .avgBpm(avgBpm)
                .build();
    }
}
