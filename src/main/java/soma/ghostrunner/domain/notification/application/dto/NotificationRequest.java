package soma.ghostrunner.domain.notification.application.dto;

import io.jsonwebtoken.lang.Assert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.notification.domain.Notification;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class NotificationRequest {
    private String title;
    private String body;
    private List<NotificationIds> ids;
    private Map<String, Object> data;

    @Getter
    @AllArgsConstructor
    public static class NotificationIds {
        Long notificationId;
        String pushTokenId;
    }

    public static NotificationRequest from(List<Notification> notifications) {
        Assert.notNull(notifications, "Notifications must not be null");
        if (notifications.isEmpty()) {
            return NotificationRequest.builder().build();
        }

        NotificationRequest request = NotificationRequest.builder().build();
        request.title = notifications.get(0).getTitle();
        request.body = notifications.get(0).getBody();
        request.data = notifications.get(0).getData();
        request.ids = notifications.stream()
                .map(noti -> new NotificationIds(noti.getId(), noti.getPushToken().getToken()))
                .toList();
        return request;
    }
}
