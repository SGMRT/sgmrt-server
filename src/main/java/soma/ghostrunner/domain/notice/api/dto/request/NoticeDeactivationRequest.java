package soma.ghostrunner.domain.notice.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class NoticeDeactivationRequest {
    private List<Long> noticeIds;
}
