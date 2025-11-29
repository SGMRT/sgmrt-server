package soma.ghostrunner.domain.notice.domain.event;

import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;
import java.util.List;

public record NoticeActivatedEvent(
        List<NoticeRecord> activatedNotices
) {

    public static NoticeActivatedEvent from(List<Notice> activatedNotices) {
        return new NoticeActivatedEvent(activatedNotices.stream()
                .map(NoticeRecord::from)
                .toList());
    }

    public record NoticeRecord (
            Long noticeId,
            NoticeType noticeType,
            String title,
            String content,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        public static NoticeRecord from(Notice notice) {
            return new NoticeRecord(
                    notice.getId(),
                    notice.getType(),
                    notice.getTitle(),
                    notice.getContent(),
                    notice.getStartAt(),
                    notice.getEndAt()
            );
        }
    }
}
