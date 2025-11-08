package soma.ghostrunner.domain.notification.application.dto;

import java.util.List;
import java.util.Map;

public record PushMessage(
        List<String> pushTokens,
        String title,
        String body,
        Map<String, Object> data
) {
    public static PushMessage of (List<String> pushTokens, String title, String body, Map<String, Object> data) {
        return new PushMessage(pushTokens, title, body, data);
    }
}
