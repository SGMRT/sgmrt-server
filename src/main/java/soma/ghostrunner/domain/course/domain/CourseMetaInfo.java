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
    private Double distance;

    @NotEmpty @Column(name = "elevation_gain_m")
    private Integer elevationGain;

    @NotEmpty @Column(name = "elevation_loss_m")
    private Integer elevationLoss;

    @Builder
    private CourseMetaInfo(Double distance, Integer  elevationGain, Integer elevationLoss) {
        this.distance = distance;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
    }

    public static CourseMetaInfo of(Double distance, Integer  elevationGain, Integer elevationLoss) {
        return CourseMetaInfo.builder()
                .distance(distance)
                .elevationGain(elevationGain)
                .elevationLoss(elevationLoss)
                .build();
    }
}
