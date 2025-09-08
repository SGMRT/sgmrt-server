package soma.ghostrunner.domain.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class NotificationSendRequest {
    private List<Long> userIds;
    private String title;
    private String body;
}
