package soma.ghostrunner.domain.course.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import soma.ghostrunner.domain.course.enums.CourseSource;

import java.time.LocalDateTime;

@Getter
public class CourseMetaInfoDto {

    private Long courseId;
    private Long ownerId;
    private String name;
    private CourseSource source;
    private Double startLatitude;
    private Double startLongitude;
    private String routeUrl;

    private Double distanceKm;
    private Double elevationAverage;
    private String thumbnailUrl;

    private LocalDateTime createdAt;

    @QueryProjection
    public CourseMetaInfoDto(
            Long courseId, Long ownerId,
            String name, CourseSource source, Double startLatitude, Double startLongitude, String routeUrl,
            Double distanceKm, Double elevationAverage, String thumbnailUrl,
            LocalDateTime createdAt) {
        this.courseId = courseId;
        this.ownerId = ownerId;
        this.name = name;
        this.source = source;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.routeUrl = routeUrl;
        this.distanceKm = distanceKm;
        this.elevationAverage = elevationAverage;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = createdAt;
    }

}
