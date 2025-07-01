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

    @Column(name = "distanceM", nullable = false)
    private Double distanceM;

    @NotEmpty @Column(name = "elevation_gain_m")
    private Integer elevationGainM;

    @NotEmpty @Column(name = "elevation_loss_m")
    private Integer elevationLossM;

    @Builder
    private CourseProfile(Double distanceM, Integer elevationGainM, Integer elevationLossM) {
        this.distanceM = distanceM;
        this.elevationGainM = elevationGainM;
        this.elevationLossM = elevationLossM;
    }

    public static CourseProfile of(Double distance, Integer  elevationGain, Integer elevationLoss) {
        return CourseProfile.builder()
                .distanceM(distance)
                .elevationGainM(elevationGain)
                .elevationLossM(elevationLoss)
                .build();
    }
}
