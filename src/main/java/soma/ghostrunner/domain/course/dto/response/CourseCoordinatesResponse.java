package soma.ghostrunner.domain.course.dto.response;

import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

import java.util.List;

public record CourseCoordinatesResponse(
  String name,
  List<CoordinateDto> coordinates
) {}
