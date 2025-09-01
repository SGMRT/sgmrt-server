package soma.ghostrunner.domain.notice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.Assert;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    private String content;

    @Setter
    private String imageUrl;

    @Setter
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer priority;

    @Setter
    private LocalDateTime startAt;

    @Setter
    private LocalDateTime endAt;

    public static Notice of(String title, String content, String imageUrl) {
        if(title == null && content == null && imageUrl == null) throw new IllegalArgumentException();
        return new Notice(null, title, content, imageUrl, 0, LocalDateTime.now(), null);
    }


    public static Notice of(String title, String content, String imageUrl, Integer priority,
                            LocalDateTime startAt, LocalDateTime endAt) {
        if(title == null && content == null && imageUrl == null) throw new IllegalArgumentException();
        if(priority == null) priority = 0;
        if(startAt == null) startAt = LocalDateTime.now();
        if(endAt != null) { // startAt도 null이 아닌 경우에만 비교
            Assert.isTrue(!startAt.isAfter(endAt), "startAt cannot be after endAt");
        }

        return new Notice(null, title, content, imageUrl, priority, startAt, endAt);
    }

}
