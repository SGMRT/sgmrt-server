package soma.ghostrunner.domain.notification.application.dto;

import java.util.List;
import java.util.Map;

public record PushMessage(
        List<String> pushTokens,
        String title,
        String body,
        Map<String, Object> data,
        String messageUuid) {

    public static PushMessage of (List<String> pushTokens, String title, String body, Map<String, Object> data, String messageUuid) {
        return new PushMessage(pushTokens, title, body, data, messageUuid);
    }

}
