package soma.ghostrunner.domain.notification.client;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import com.niamedtech.expo.exposerversdk.response.Status;
import com.niamedtech.expo.exposerversdk.response.TicketResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.notification.application.dto.NotificationRequest;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

@DisplayName("ExpoPushClient 유닛 테스트")
@ExtendWith(MockitoExtension.class)
class ExpoPushClientTest {

    @InjectMocks
    private ExpoPushClient expoPushClient;

    @Mock
    private ExpoPushNotificationClient expoPushNotificationClient;

    @DisplayName("Expo 서버로부터 성공 응답을 받으면 성공 결과를 반환한다.")
    @Test
    void pushAsync_success() throws Exception {
        // given
        NotificationRequest request = createRequest(1L, "test-token");
        List<TicketResponse.Ticket> tickets = createSuccessTickets("ticket-id-1");
        given(expoPushNotificationClient.sendPushNotifications(anyList())).willReturn(tickets);

        // when
        List<NotificationSendResult> results = expoPushClient.pushAsync(request).join();

        // then
        assertThat(results).hasSize(1);
        NotificationSendResult result = results.get(0);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.notificationId()).isEqualTo(1L);
        assertThat(result.ticketId()).isEqualTo("ticket-id-1");
    }

    @DisplayName("Expo 서버로부터 에러 응답을 받으면 실패 결과를 반환한다.")
    @Test
    void pushAsync_Failure() throws Exception {
        // given
        NotificationRequest request = createRequest(1L, "invalid-token");
        List<TicketResponse.Ticket> failureTickets = createFailureTickets("실패해부렸으");
        given(expoPushNotificationClient.sendPushNotifications(anyList())).willReturn(failureTickets);

        // when
        List<NotificationSendResult> results = expoPushClient.pushAsync(request).join();

        // then
        assertThat(results).hasSize(1);
        NotificationSendResult result = results.get(0);
        assertThat(result.notificationId()).isEqualTo(1L);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("실패해부렸으");
    }

    @DisplayName("여러 알림을 전송하여 각각 성공 및 실패 응답을 받으면 각 결과를 순서대로 반환한다.")
    @Test
    void pushAsync_mixedResults() throws IOException {
        // given
        NotificationRequest request = createRequest(List.of(1L, 2L), List.of("valid-token", "invalid-token"));
        // 첫 결과는 성공, 다음 결과는 실패
        List<TicketResponse.Ticket> mixedTickets = new ArrayList<>(createSuccessTickets("success-ticket"));
        mixedTickets.addAll(createFailureTickets("이거 아니에유"));
        given(expoPushNotificationClient.sendPushNotifications(anyList())).willReturn(mixedTickets);

        // when
        List<NotificationSendResult> results = expoPushClient.pushAsync(request).join();

        // then
        assertThat(results).hasSize(2);
        NotificationSendResult successResult = results.get(0);
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.notificationId()).isEqualTo(1L);
        assertThat(successResult.ticketId()).isEqualTo("success-ticket");

        NotificationSendResult failureResult = results.get(1);
        assertThat(failureResult.isSuccess()).isFalse();
        assertThat(failureResult.notificationId()).isEqualTo(2L);
        assertThat(failureResult.errorMessage()).isEqualTo("이거 아니에유");
    }

    @DisplayName("알림 전송 중 IOException 발생 시 CompletableFuture가 실패한다.")
    @Test
    void pushAsync_throwsIOException() throws IOException {
        // given
        NotificationRequest request = createRequest(1L, "test-token");
        given(expoPushNotificationClient.sendPushNotifications(anyList())).willThrow(new IOException("Network error"));

        // when & then
        assertThrows(CompletionException.class, () -> expoPushClient.pushAsync(request).join());
    }

    // --- helper methods ---
    private NotificationRequest createRequest(Long notificationId, String pushToken) {
        NotificationRequest.NotificationIds notificationIds = new NotificationRequest.NotificationIds(notificationId, pushToken);
        return new NotificationRequest("알림 제목", "알림 본문", List.of(notificationIds), Collections.emptyMap());
    }

    private NotificationRequest createRequest(List<Long> notificationIds, List<String> pushTokens) {
        if (notificationIds.size() != pushTokens.size()) {
            throw new IllegalArgumentException("notificationIds and pushTokens must have the same size");
        }

        List<NotificationRequest.NotificationIds> ids = new ArrayList<>();
        for (int i = 0; i < notificationIds.size(); i++) {
            ids.add(new NotificationRequest.NotificationIds(notificationIds.get(i), pushTokens.get(i)));
        }
        return new NotificationRequest("알림 제목", "알림 본문", ids, Collections.emptyMap());
    }

    private List<TicketResponse.Ticket> createSuccessTickets(String ticketId) {
        TicketResponse.Ticket ticket = new TicketResponse.Ticket();
        ticket.setId(ticketId);
        ticket.setStatus(Status.OK);
        return List.of(ticket);
    }

    private List<TicketResponse.Ticket> createFailureTickets(String failureMessage) {
        TicketResponse.Ticket ticket = new TicketResponse.Ticket();
        ticket.setStatus(Status.ERROR);
        ticket.setMessage(failureMessage);
        return List.of(ticket);
    }
}