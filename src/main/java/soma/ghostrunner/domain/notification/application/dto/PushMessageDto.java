package soma.ghostrunner.domain.notification.application.dto;

import java.util.Map;

public record PushMessageDto (
    String pushToken,
    String title,
    String body,
    Map<String, Object> data,
    String versionRange
) {}
