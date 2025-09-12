package soma.ghostrunner.domain.course.dto.response;

import java.time.LocalDateTime;

public record CourseDetailedResponse (
  // 코스 정보
  Long id,
  String name,
  String telemetryUrl, // 코스 시계열 데이터
  String checkpointsUrl,
  Integer distance,
  Integer elevationAverage,
  Integer elevationGain,
  Integer elevationLoss,
  LocalDateTime createdAt,

  // 전체 러닝 기록 통계
  Integer averageCompletionTime,
  Integer averageFinisherPace,
  Integer averageFinisherCadence,
  Integer averageCaloriesBurned,
  Integer lowestFinisherPace,

  Integer uniqueRunnersCount,
  Integer totalRunsCount,

  // 사용자 기록 통계
  Double myLowestPace,
  Double myAveragePace,
  Double myHighestPace,

  // 코스에 등록된 사용자 고스트 정보
  CourseGhostResponse myGhostInfo
) {}
