package soma.ghostrunner.domain.notification.client;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.request.PushNotification;
import com.niamedtech.expo.exposerversdk.response.TicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.notification.application.dto.NotificationRequest;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpoPushClient {

    private final ExpoPushNotificationClient pushClient;
    private final Executor pushTaskExecutor;

    public CompletableFuture<List<NotificationSendResult>> pushAsync(NotificationRequest request) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    PushNotification notification = createPushNotification(request);
                    List<TicketResponse.Ticket> tickets = pushClient.sendPushNotifications(List.of(notification));
                    return mapToNotificationSendResults(request, tickets);
                } catch (Exception e) {
                    log.error("ExpoPushClient: Expo push failed with exception for request {}: {}", request, e.getMessage(), e);
                    throw new CompletionException(e);
                }
            }, pushTaskExecutor);
    }

    private static List<NotificationSendResult> mapToNotificationSendResults(NotificationRequest request,
                                                                             List<TicketResponse.Ticket> tickets) {
        List<NotificationSendResult> results = new ArrayList<>();
        if(tickets.size() != request.getIds().size()) {
            throw new RuntimeException("Ticket size and request size do not match - tickets: " + tickets.size() + ", request: " + request.getIds().size());
        }

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
        return results;
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
