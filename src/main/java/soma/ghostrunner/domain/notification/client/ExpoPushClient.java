package soma.ghostrunner.domain.notification.client;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.request.PushNotification;
import com.niamedtech.expo.exposerversdk.response.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.notification.application.dto.NotificationRequest;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ExpoPushClient {

    private final ExpoPushNotificationClient pushClient;

    @Async("pushTaskExecutor")
    public CompletableFuture<List<NotificationSendResult>> pushAsync(NotificationRequest request) {
        try {
            PushNotification notification = createPushNotification(request);
            List<TicketResponse.Ticket> tickets = pushClient.sendPushNotifications(List.of(notification));

            List<NotificationSendResult> results = new ArrayList<>();
            int i = 0;
            for(var ticket : tickets) {
                switch (ticket.getStatus()) {
                    case OK -> results.add(NotificationSendResult
                                .ofSuccess(request.getIds().get(i).getNotificationId(), ticket.getId()));
                    case ERROR -> results.add(NotificationSendResult
                                .ofFailure(request.getIds().get(i).getNotificationId(), ticket.getMessage()));
                }
                i++;
            }
            return CompletableFuture.completedFuture(results);
        } catch (IOException e) {
            // todo 나중에 채우기
            return CompletableFuture.failedFuture(e);
        }
    }

    private PushNotification createPushNotification(NotificationRequest request) {
        PushNotification notification = new PushNotification();
        notification.setTitle(request.getTitle());
        notification.setBody(request.getBody());
        notification.setData(request.getData());
        notification.setTo(request.getIds().stream()
                .map(NotificationRequest.NotificationIds::getPushTokenId)
                .toList());
        return notification;
    }

}
