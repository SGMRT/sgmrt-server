package soma.ghostrunner.domain.notice.api.dto;

import org.mapstruct.Mapper;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.domain.Notice;

@Mapper(componentModel = "spring")
public interface NoticeMapper {

    NoticeDetailedResponse toDetailedResponse(Notice notice);

}
