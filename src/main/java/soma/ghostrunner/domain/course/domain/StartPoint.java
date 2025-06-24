package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StartPoint {

    @Column(name = "start_latitude", nullable = false)
    private double latitude;

    @Column(name = "start_longtitude", nullable = false)
    private double longitude;

    @Builder
    private StartPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static StartPoint fromCoordinates(double latitude, double longitude) {
        return StartPoint.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }
}
