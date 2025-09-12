package soma.ghostrunner.domain.course.dto.response;

import soma.ghostrunner.domain.running.domain.path.Coordinates;

import java.util.List;

public record CourseCoordinatesResponse(
  String name,
  List<Coordinates> coordinates
) {}
