package soma.ghostrunner.domain.course.dto.response;

import java.util.List;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

public record CourseResponse (
    Long id,
    String name,
    Double startLat,
    Double startLng,
    List<CoordinateDto> pathData,
    Integer distance,
    Integer elevationGain,
    Integer elevationLoss
) {}
