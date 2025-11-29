package soma.ghostrunner.domain.course.dto;

import soma.ghostrunner.domain.course.enums.CourseSource;

import java.time.LocalDateTime;

public record CoursePreviewDto(
    Long id,
    String name,
    String ownerUuid,
    Double startLat,
    Double startLng,
    CourseSource source,
    String routeUrl,
    String checkpointsUrl,
    String thumbnailUrl,
    Integer distance,
    Integer elevationAverage,
    Integer elevationGain,
    Integer elevationLoss,
    LocalDateTime createdAt
) {}
