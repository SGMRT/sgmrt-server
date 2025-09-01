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
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "notice_id"})) // 동일한 공지 중복 숨김처리 방지
public class NoticeDismissal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne
    @JoinColumn(name = "notice_id")
    private Notice notice;

    @Column(name = "dismiss_until", nullable = false)
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
