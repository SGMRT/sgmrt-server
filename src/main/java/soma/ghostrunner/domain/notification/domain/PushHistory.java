package soma.ghostrunner.domain.notification.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "push_history", indexes = {
        @Index(name = "idx_push_history_member_id", columnList = "memberId"),
})
public class PushHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    @Column(nullable = false)
    private Long memberId;

    @Column
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.CREATED;

    @Column
    private String title;

    @Column
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime readAt;

    public static PushHistory of(Long memberId, String title, String body, Map<String, Object> data) {
        return PushHistory.builder()
                .memberId(memberId)
                .title(title)
                .body(body)
                .data(data)
                .build();
    }

    public void markAsRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.status = NotificationStatus.DELIVERED;
            this.readAt = readAt;
        }
    }

}
