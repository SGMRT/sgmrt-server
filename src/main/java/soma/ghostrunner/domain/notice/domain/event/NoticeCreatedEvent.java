package soma.ghostrunner.domain.notice.domain.event;

import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;

public record NoticeCreatedEvent (
    Long noticeId,
    NoticeType noticeType,
    String title,
    String content,
    LocalDateTime startAt,
    LocalDateTime endAt
) {}
