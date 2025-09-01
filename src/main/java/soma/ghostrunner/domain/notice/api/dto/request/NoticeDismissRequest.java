package soma.ghostrunner.domain.notice.api.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NoticeDismissRequest {
    Integer dismissDays;
}
