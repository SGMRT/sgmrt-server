package soma.ghostrunner.domain.course.dto.query;

public record RunnerSummary (
  String uuid,
  String profileImageUrl,
  int recordInSeconds
) {}
