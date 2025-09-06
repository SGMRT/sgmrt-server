package soma.ghostrunner.domain.notice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.Assert;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.net.URL;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "notice", indexes = {
        @Index(name = "idx_notice_active_period", columnList = "start_at, end_at, priority, created_at"),
        @Index(name = "idx_notice_created_at", columnList = "created_at")
})
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2048)
    private String content;

    @Column(length = 2048)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer priority;

    private LocalDateTime startAt;

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

    public void updateTitle(String title) {
        Assert.notNull(title, "title은 null로 변경할 수 없습니다.");
        this.title = title;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateImageUrl(String imageUrl) {
        try {
            new URL(imageUrl).toURI(); // 형식 검증
            this.imageUrl = imageUrl;
        } catch (Exception e) {
            throw new IllegalArgumentException("imageUrl이 URL 형식이 아닙니다:" + imageUrl);
        }
    }

    public void updatePriority(Integer priority) {
        Assert.notNull(priority, "Priority는 null로 변경할 수 없습니다.");
        this.priority = priority;
    }

    public void updateStartAt(LocalDateTime startAt) {
        Assert.notNull(startAt, "공지 노출 시작기간은 null로 변경할 수 없습니다.");
        if(endAt != null && startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("공지 노출 시작기간은 종료기간보다 앞이어야 합니다.");
        }
        this.startAt = startAt;
    }

    public void updateEndAt(LocalDateTime endAt) {
        if(endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("공지 노출 종료기간은 시작기간보다 뒤여야 합니다.");
        }
        this.endAt = endAt;
    }

}
