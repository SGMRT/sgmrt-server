package soma.ghostrunner.domain.notification.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import soma.ghostrunner.global.common.converter.JsonToMapConverter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Convert(converter = JsonToMapConverter.class)
    private Map<String, Object> data;

    @Builder.Default
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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
