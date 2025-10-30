package soma.ghostrunner.domain.notice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.Assert;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.domain.notice.exception.NoticeAlreadyActivatedException;
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

    @Enumerated(EnumType.STRING)
    private NoticeType type;

    @Column(length = 2048)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer priority;

    // 공지 노출 시작기간 -> null이면 노출되지 않음 (시작기간과 종료기간이 모두 null인 경우 비활성화 상태)
    private LocalDateTime startAt;

    // 공지 노출 종료기간 -> null이면 노출되지 않음 (시작기간과 종료기간이 모두 null인 경우 비활성화 상태)
    private LocalDateTime endAt;

    public static Notice of(String title, String content, String imageUrl) {
        if(title == null && content == null && imageUrl == null) throw new IllegalArgumentException();
        return new Notice(null, title, content, NoticeType.GENERAL_V2, imageUrl, 0, LocalDateTime.now(), null);
    }

    public static Notice of(String title, String content, NoticeType noticeType, String imageUrl, Integer priority) {
        return of(title, content, noticeType, imageUrl, priority, null, null);
    }

    public static Notice of(String title, String content, NoticeType noticeType, String imageUrl, Integer priority,
                            LocalDateTime startAt, LocalDateTime endAt) {
        if(title == null && content == null && imageUrl == null) throw new IllegalArgumentException();
        if(noticeType == null) noticeType = NoticeType.GENERAL_V2;
        if(priority == null) priority = 0;
        if(endAt != null) { // startAt도 null이 아닌 경우에만 비교
            Assert.notNull(startAt, "endAt이 지정된 경우 startAt은 null일 수 없습니다.");
            Assert.isTrue(!startAt.isAfter(endAt), "startAt이 endAt보다 이후일 수 없습니다.");
        }

        return new Notice(null, title, content, noticeType, imageUrl, priority, startAt, endAt);
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
        Assert.notNull(this.endAt, "공지 노출 종료기간이 null인 상태에서 시작기간을 변경할 수 없습니다.");
        if(endAt != null && startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("공지 노출 시작기간은 종료기간보다 앞이어야 합니다.");
        }
        this.startAt = startAt;
    }

    public void updateType(NoticeType type) {
        Assert.notNull(type, "공지 유형은 null로 변경할 수 없습니다.");
        Assert.isTrue(!NoticeType.getDeprecatedTypes().contains(type), "V1 유형은 더 이상 사용되지 않습니다.");
        this.type = type;
    }

    public void updateEndAt(LocalDateTime endAt) {
        Assert.notNull(endAt, "공지 노출 종료기간은 null로 변경할 수 없습니다.");
        Assert.notNull(this.startAt, "공지 노출 시작기간이 null인 상태에서 종료기간을 변경할 수 없습니다.");
        if(endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("공지 노출 종료기간은 시작기간보다 뒤여야 합니다.");
        }
        this.endAt = endAt;
    }

    public void activate(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("노출 시작 시각 혹은 종료 시각은 null일 수 없습니다.");
        }
        if (this.startAt != null && this.endAt != null) {
            throw new NoticeAlreadyActivatedException("공지사항 id " + id + "는 이미 활성화 상태입니다.");
        }
        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("공지 노출 시작기간은 종료기간보다 앞이어야 합니다.");
        }
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void deactivate() {
        this.startAt = null;
        this.endAt = null;
    }

}
