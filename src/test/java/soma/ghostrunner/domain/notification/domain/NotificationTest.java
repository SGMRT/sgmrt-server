package soma.ghostrunner.domain.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class NotificationTest {

    @DisplayName("Notification을 정적 팩토리 메서드 of()로 생성할 수 있다.")
    @Test
    void createNotification() {
        // given
        Member member = Member.of("흰둥이", "test-url");
        PushToken pushToken = new PushToken(member, "test-token");
        String title = "알림 제목";
        String body = "알림 본문";
        Map<String, Object> data = Map.of("key", "value");

        // when
        Notification notification = Notification.of(pushToken, title, body, data);

        // then
        assertNotNull(notification);
        assertThat(notification.getPushToken()).isEqualTo(pushToken);
        assertThat(notification.getTitle()).isEqualTo(title);
        assertThat(notification.getBody()).isEqualTo(body);
        assertThat(notification.getData()).isEqualTo(data);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CREATED);
    }

    @DisplayName("Notification 생성 시 제목과 본문이 동시에 null이면 예외가 발생한다.")
    @Test
    void createNotificationWithNullTitleAndBody() {
        // given
        Member member = Member.of("흰둥이", "test-url");
        PushToken pushToken = new PushToken(member, "test-token");

        // when & then
        assertThatThrownBy(() -> Notification.of(pushToken, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목과 본문이 동시에 null일 수 없음");
    }

    @DisplayName("Notification의 상태를 SENT로 변경할 수 있다.")
    @Test
    void markAsSent() {
        // given
        Member member = Member.of("짱구", "test-url");
        PushToken pushToken = new PushToken(member, "push-token");
        Notification notification = Notification.of(pushToken, "제목", "본문", Map.of());
        String ticketId = "expo-ticket-id";

        // when
        notification.markAsSent(ticketId);

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getTicketId()).isEqualTo(ticketId);
    }

    @DisplayName("Notification의 상태를 DELIVERED로 변경할 수 있다.")
    @Test
    void markAsDelivered() {
        // given
        Notification notification = createSentNotification();

        // when
        notification.markAsDelivered();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @DisplayName("Notification의 상태를 RETRYING로 변경할 수 있다.")
    @Test
    void markAsRetrying() {
        // given
        Notification notification = createSentNotification();

        // when
        notification.markAsRetrying();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRYING);
    }

    @DisplayName("Notification의 상태를 FAILED로 변경할 수 있다.")
    @Test
    void markAsFailed() {
        // given
        Notification notification = createSentNotification();

        // when
        notification.markAsFailed();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    private Notification createSentNotification() {
        Member member = Member.of("신형만", "test-url");
        PushToken pushToken = new PushToken(member, "push-token");
        Notification notification = Notification.of(pushToken, "알림 제목", "알림 본문", Map.of());
        notification.markAsSent("expo-ticket-id");
        return notification;
    }

}