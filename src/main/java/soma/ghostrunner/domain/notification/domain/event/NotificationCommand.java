package soma.ghostrunner.domain.notification.domain.event;

import java.util.List;
import java.util.Map;

public record NotificationCommand(
        List<Long> memberIds,
        String title,
        String body,
        Map<String, Object> data
) {
    public static NotificationCommand of(List<Long> memberIds, String title, String body, Map<String, Object> data) {
        return new NotificationCommand(memberIds, title, body, data);
    }
}