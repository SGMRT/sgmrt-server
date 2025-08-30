package soma.ghostrunner.domain.notice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "notice_id"})) // 동일한 공지 중복 숨김처리 방지
public class MemberNoticeDismissal extends BaseTimeEntity {

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

}
