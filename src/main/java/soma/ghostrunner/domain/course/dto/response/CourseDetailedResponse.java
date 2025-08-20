package soma.ghostrunner.domain.course.dto.response;

import java.time.LocalDateTime;

public record CourseDetailedResponse (
  Long id,
  String name,
  String telemetryUrl,
  String checkpointsUrl,
  Integer distance,
  Integer elevationAverage,
  Integer elevationGain,
  Integer elevationLoss,
  LocalDateTime createdAt,

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
