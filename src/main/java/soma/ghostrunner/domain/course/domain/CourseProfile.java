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

    @NotEmpty @Column(name = "elevation_gain_m")
    private Integer elevationGain;

    @NotEmpty @Column(name = "elevation_loss_m")
    private Integer elevationLoss;

    @Builder
    private CourseProfile(Double distance, Integer  elevationGain, Integer elevationLoss) {
        this.distance = distance;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
    }

    public static CourseProfile of(Double distance, Integer  elevationGain, Integer elevationLoss) {
        return CourseProfile.builder()
                .distance(distance)
                .elevationGain(elevationGain)
                .elevationLoss(elevationLoss)
                .build();
    }
}
