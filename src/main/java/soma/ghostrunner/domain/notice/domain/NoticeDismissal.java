package soma.ghostrunner.domain.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.LocalDateTime;

/** 공지 숨김 정보 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "notice_dismissal",
        // 동일한 공지에 중복 숨김 처리 방지 + 인덱스 생성
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "notice_id"}))
public class NoticeDismissal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id",  nullable = false)
    private Notice notice;

    @Column(name = "dismiss_until")
    private LocalDateTime dismissUntil;

    public static NoticeDismissal of(Member member, Notice notice) {
        return new NoticeDismissal(null, member, notice, null);
    }

    public static NoticeDismissal of(Member member, Notice notice, LocalDateTime dismissUntil) {
        return new NoticeDismissal(null, member, notice, dismissUntil);
    }

    public void updateDismissUntil(LocalDateTime dismissUntil) {
        this.dismissUntil = dismissUntil;
    }

}
