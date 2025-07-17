package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.domain.member.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAuthInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_auth_uid", nullable = false, unique = true)
    private String externalAuthUid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder(access = AccessLevel.PRIVATE)
    public MemberAuthInfo(Member member, String externalAuthUid) {
        this.member = member;
        this.externalAuthUid = externalAuthUid;
    }

    public static MemberAuthInfo of(Member member, String externalAuthUid) {
        return MemberAuthInfo.builder()
                .member(member)
                .externalAuthUid(externalAuthUid)
                .build();
    }

}
