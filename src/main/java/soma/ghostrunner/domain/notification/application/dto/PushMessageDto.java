package soma.ghostrunner.domain.notification.application.dto;

import java.util.Map;

public record PushMessageDto (
    Long memberId,
    String versionRange,
    String title,
    String body,
    Map<String, Object> data
) {}
