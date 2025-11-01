package soma.ghostrunner.domain.notification.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class NotificationRequest {
    private String title;
    private String body;
    private Map<String, Object> data;
    private List<String> targetPushTokens;

    @Getter
    @AllArgsConstructor
    @ToString
    public static class NotificationIds {
        Long notificationId;
        String pushTokenId;
    }

    public static NotificationRequest of(String title, String body, Map<String, Object> data, List<String> targetPushTokens) {
        return NotificationRequest.builder()
                .title(title)
                .body(body)
                .data(data)
                .targetPushTokens(targetPushTokens)
                .build();
    }
}
