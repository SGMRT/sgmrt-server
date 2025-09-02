package soma.ghostrunner.domain.notice.api.dto.response;

import java.time.LocalDateTime;

public record NoticeDetailedResponse (
  Long id,
  String title,
  String imageUrl,
  String content,
  Integer priority,
  LocalDateTime startAt,
  LocalDateTime endAt
) {}
