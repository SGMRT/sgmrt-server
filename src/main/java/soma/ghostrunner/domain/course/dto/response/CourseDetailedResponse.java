package soma.ghostrunner.domain.course.dto.response;

public record CourseDetailedResponse (
  Long id,
  String name,
  Integer distance,
  Integer elevationGain,
  Integer elevationLoss,
  Integer averageCompletionTime,
  Integer averageFinisherPace,
  Integer averageFinisherCadence,
  Integer lowestFinisherPace
) {}
