package soma.ghostrunner.domain.notification.client;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.request.PushNotification;
import com.niamedtech.expo.exposerversdk.response.TicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.notification.application.dto.PushMessage;
import soma.ghostrunner.domain.notification.application.dto.PushSendResult;

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

    public List<PushSendResult> push(PushMessage request) throws IOException {
        PushNotification notification = createPushNotification(request);
        List<TicketResponse.Ticket> tickets = pushClient.sendPushNotifications(List.of(notification));
        return mapToNotificationSendResults(request, tickets);
    }

    public CompletableFuture<List<PushSendResult>> pushAsync(PushMessage request) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return push(request);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, pushTaskExecutor);
    }

    private static List<PushSendResult> mapToNotificationSendResults(PushMessage request,
                                                                     List<TicketResponse.Ticket> tickets) {
        List<PushSendResult> results = new ArrayList<>();
        if(tickets.size() != request.pushTokens().size()) {
            throw new RuntimeException("Ticket size and request size do not match - tickets: " + tickets.size() + ", request: " + request.pushTokens().size());
        }

        int i = 0;
        for(var ticket : tickets) {
            switch (ticket.getStatus()) {
                case OK -> results.add(PushSendResult
                            .ofSuccess(request.pushTokens().get(i), ticket.getId()));
                case ERROR -> results.add(PushSendResult
                            .ofFailure(request.pushTokens().get(i), ticket.getMessage()));
            }
            i++;
        }
        return results;
    }

    private PushNotification createPushNotification(PushMessage request) {
        PushNotification notification = new PushNotification();
        notification.setTitle(request.title());
        notification.setBody(request.body());
        notification.setData(request.data());
        notification.setTo(request.pushTokens());
        return notification;
    }

}
