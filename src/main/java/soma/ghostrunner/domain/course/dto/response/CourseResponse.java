package soma.ghostrunner.domain.course.dto.response;

import java.util.List;
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;

public record CourseResponse (
    Long id,
    String name,
    Double startLat,
    Double startLng,
    List<CourseCoordinateDto> pathData,
    Integer distance,
    Integer elevationGain,
    Integer elevationLoss
) {}
