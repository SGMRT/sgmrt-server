package soma.ghostrunner.domain.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class PushSendRequest {
    private List<Long> userIds;
    private String title;
    private String body;
    private Map<String, Object> data;
}
