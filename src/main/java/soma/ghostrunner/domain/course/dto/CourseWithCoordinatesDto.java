package soma.ghostrunner.domain.course.dto;

import java.time.LocalDateTime;

public record CourseWithCoordinatesDto(
    Long id,
    String name,
    Double startLat,
    Double startLng,
    String routeUrl,
    String thumbnailUrl,
    Integer distance,
    Integer elevationAverage,
    Integer elevationGain,
    Integer elevationLoss,
    LocalDateTime createdAt
) {}
