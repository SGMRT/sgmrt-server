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
public class CourseMetaInfo {

    @Column(name = "distance", nullable = false)
    private double distance;

    @NotEmpty @Column(name = "elevation_gain_m")
    private int elevationGain;

    @NotEmpty @Column(name = "elevation_loss_m")
    private int elevationLoss;

    @Builder
    private CourseMetaInfo(double distance, int  elevationGain, int elevationLoss) {
        this.distance = distance;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
    }

    public static CourseMetaInfo of(double distance, int  elevationGain, int elevationLoss) {
        return CourseMetaInfo.builder()
                .distance(distance)
                .elevationGain(elevationGain)
                .elevationLoss(elevationLoss)
                .build();
    }
}
