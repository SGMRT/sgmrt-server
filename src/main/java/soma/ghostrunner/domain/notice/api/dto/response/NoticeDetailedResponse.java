package soma.ghostrunner.domain.notice.api.dto.response;

import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;

public record NoticeDetailedResponse (
  Long id,
  String title,
  NoticeType type,
  String imageUrl,
  String content,
  Integer priority,
  LocalDateTime startAt,
  LocalDateTime endAt
) {}
