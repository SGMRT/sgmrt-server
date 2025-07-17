package soma.ghostrunner.domain.member;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "member")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    @Embedded
    private MemberBioInfo bioInfo;

    @Column(name = "profile_picture_url", length = 2048)
    private String profilePictureUrl;

    @Column(name = "external_auth_uid", nullable = false, unique = true)
    private String externalAuthUid;

    @Column(name = "last_login_at")
    @Setter
    private LocalDateTime lastLoginAt;

    @Builder
    public Member(String nickname, String profilePictureUrl, String uuid,
                  MemberBioInfo bioInfo, String externalAuthUid, LocalDateTime lastLoginAt) {
        this.nickname = nickname;
        this.profilePictureUrl = profilePictureUrl;
        this.uuid = uuid;
        this.bioInfo = bioInfo;
        this.externalAuthUid = externalAuthUid;
        this.lastLoginAt = lastLoginAt;
    }

    public static Member of(String nickname, String profilePictureUrl, String externalAuthUid) {
        return Member.builder()
                .nickname(nickname)
                .uuid(UUID.randomUUID().toString())
                .profilePictureUrl(profilePictureUrl)
                .externalAuthUid(externalAuthUid)
                .build();
    }
}
