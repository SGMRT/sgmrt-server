package soma.ghostrunner.domain.notification.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import soma.ghostrunner.domain.notification.api.dto.NotificationSendRequest;
import soma.ghostrunner.domain.notification.api.dto.PushTokenSaveRequest;
import soma.ghostrunner.domain.notification.application.NotificationService;
import soma.ghostrunner.domain.notification.application.dto.NotificationBatchResult;
import soma.ghostrunner.global.common.validator.auth.AdminOnly;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class NotificationApi {
    private final NotificationService notificationService;

    @AdminOnly
    @PostMapping("/v1/admin/notifications")
    public NotificationBatchResult sendNotification(@RequestBody NotificationSendRequest request) {
        try {
            CompletableFuture<NotificationBatchResult> notificationFuture = notificationService
                    .sendPushNotificationAsync(request.getUserIds(), request.getTitle(), request.getBody(), request.getData());
            return notificationFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/v1/member/{memberUuid}/push-token")
    public void updatePushToken(@PathVariable("memberUuid") String memberUuid,
                                @RequestBody PushTokenSaveRequest request) {
        notificationService.saveMemberPushToken(memberUuid, request.getPushToken());
    }

}
