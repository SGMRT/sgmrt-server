package soma.ghostrunner.domain.member;

import jakarta.persistence.*;
import lombok.*;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.common.document.TestOnly;

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

    @Column(name = "last_login_at")
    @Setter
    private LocalDateTime lastLoginAt;

    @Builder
    public Member(String nickname, String profilePictureUrl,
                  MemberBioInfo bioInfo, LocalDateTime lastLoginAt) {
        this.nickname = nickname;
        this.profilePictureUrl = profilePictureUrl;
        this.uuid = UUID.randomUUID().toString();
        this.bioInfo = bioInfo;
        this.lastLoginAt = lastLoginAt;
    }

    public static Member of(String nickname, String profilePictureUrl) {
        return Member.builder()
                .nickname(nickname)
                .profilePictureUrl(profilePictureUrl)
                .build();
    }

    @TestOnly
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

}
