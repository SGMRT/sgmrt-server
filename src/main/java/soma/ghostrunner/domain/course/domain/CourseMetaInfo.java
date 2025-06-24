package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseMetaInfo {

    @Column(name = "distance", nullable = false)
    private double distance;

    @Column(name = "altitude", nullable = false)
    private int altitude;

    @Builder
    private CourseMetaInfo(double distance, int altitude) {
        this.distance = distance;
        this.altitude = altitude;
    }

    public static CourseMetaInfo of(double distance, int altitude) {
        return CourseMetaInfo.builder()
                .distance(distance)
                .altitude(altitude)
                .build();
    }
}
