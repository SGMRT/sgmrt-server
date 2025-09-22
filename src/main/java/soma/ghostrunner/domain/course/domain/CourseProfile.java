package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseProfile {

    @Column(name = "distance_km", nullable = false)
    private Double distance;

    @Column(name = "elevation_average_m")
    private Double elevationAverage;

    @Column(name = "elevation_gain_m")
    private Double elevationGain;

    @Column(name = "elevation_loss_m")
    private Double elevationLoss;

    @Builder
    private CourseProfile(Double distance, Double elevationAverage, Double elevationGain, Double elevationLoss) {
        this.distance = distance;
        this.elevationAverage = elevationAverage;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
    }

    public static CourseProfile of(Double distance, Double elevationAverage, Double elevationGain, Double elevationLoss) {
        return CourseProfile.builder()
                .distance(distance)
                .elevationAverage(elevationAverage)
                .elevationGain(elevationGain)
                .elevationLoss(elevationLoss)
                .build();
    }

}
