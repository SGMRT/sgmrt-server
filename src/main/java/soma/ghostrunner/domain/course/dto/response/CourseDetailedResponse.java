package soma.ghostrunner.domain.course.dto.response;

public record CourseDetailedResponse (
  Long id,
  String name,
  String telemetryUrl,
  Integer distance,
  Integer elevationGain,
  Integer elevationLoss,

  Integer averageCompletionTime,
  Integer averageFinisherPace,
  Integer averageFinisherCadence,
  Integer averageCaloriesBurned,
  Integer lowestFinisherPace,

  Integer uniqueRunnersCount,
  Integer totalRunsCount,

  Double myLowestPace,
  Double myAveragePace,
  Double myHighestPace
) {}
