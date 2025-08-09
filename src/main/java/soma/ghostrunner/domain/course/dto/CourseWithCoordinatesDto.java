package soma.ghostrunner.domain.course.dto;

import java.util.List;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

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
