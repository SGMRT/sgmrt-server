package soma.ghostrunner.domain.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.common.converter.JsonToMapConverter;

import java.util.Map;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "push_token_id", nullable = false)
    private PushToken pushToken;

    @Column
    private String title;

    @Column
    private String body;

    @Convert(converter = JsonToMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.CREATED;

    private String ticketId; // Expo Push Ticket Id

    public static Notification of(PushToken pushToken, String title, String body, Map<String, Object> data) {
        if(title == null && body == null) {
            throw new IllegalArgumentException("제목과 본문이 동시에 null일 수 없음");
        }
        Notification notification = new Notification();
        notification.pushToken = pushToken;
        notification.title = title;
        notification.body = body;
        notification.data = data;
        return notification;
    }

    public void markAsSent(String ticketId) {
        if(this.status != NotificationStatus.CREATED) {
            return;
        }
        this.ticketId = ticketId;
        this.status = NotificationStatus.SENT;
    }

    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
    }

    public void markAsRetrying() {
        this.status = NotificationStatus.RETRYING;
    }

    public void markAsFailed() {
        this.status = NotificationStatus.FAILED;
    }

}
