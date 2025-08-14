package soma.ghostrunner.domain.course.dto;


public record CourseWithCoordinatesDto(
    Long id,
    String name,
    Double startLat,
    Double startLng,
    String routeUrl,
    String thumbnailUrl,
    Integer distance,
    Integer elevationGain,
    Integer elevationLoss
) {}
