package soma.ghostrunner.domain.notice.api.dto;

import org.mapstruct.Mapper;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.event.NoticeActivatedEvent;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NoticeMapper {

    NoticeDetailedResponse toDetailedResponse(Notice notice);

    default NoticeActivatedEvent toNoticeActivatedEvent(List<Notice> notice) {
        return NoticeActivatedEvent.from(notice);
    }

}
