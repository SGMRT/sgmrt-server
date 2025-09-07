package soma.ghostrunner.domain.notification.domain.event;

import java.util.List;
import java.util.Map;

public record NotificationEvent(
        List<Long> userIds,
        String title,
        String body,
        Map<String, Object> data
) {
}