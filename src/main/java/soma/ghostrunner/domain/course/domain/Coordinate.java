package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordinate {

    @Column(name = "start_latitude", nullable = false)
    private Double latitude;

    @Column(name = "start_longtitude", nullable = false)
    private Double longitude;

    @Builder
    private Coordinate(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Coordinate of(Double latitude, Double longitude) {
        return Coordinate.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

}
