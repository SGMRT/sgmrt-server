package soma.ghostrunner.domain.notification.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import soma.ghostrunner.domain.notification.api.dto.NotificationCreationRequest;
import soma.ghostrunner.domain.notification.application.NotificationService;
import soma.ghostrunner.domain.notification.application.dto.NotificationBatchResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/notifications")
public class NotificationApi {
    private final NotificationService notificationService;

    @PostMapping
    public NotificationBatchResult sendNotification(@RequestBody NotificationCreationRequest request) {
        // todo 관리자 API
        try {
            CompletableFuture<NotificationBatchResult> notificationFuture = notificationService
                    .sendPushNotificationAsync(request.getUserIds(), request.getTitle(), request.getBody(), null);
            return notificationFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
