package soma.ghostrunner.domain.notice.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class NoticeActivationRequest {
    private List<Long> noticeIds;
    private LocalDateTime endAt;
}
