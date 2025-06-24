package soma.ghostrunner.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.global.common.BaseTimeEntity;

@Entity
@Table(name = "member")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    @Embedded
    private MemberBioInfo bioInfo;

    @Column(name = "profile_picture_url", length = 2048)
    private String profilePictureUrl;

    @Builder
    public Member(String nickname, String profilePictureUrl) {
        this.nickname = nickname;
        this.profilePictureUrl = profilePictureUrl;
    }

    public static Member of(String nickname, String profilePictureUrl) {
        return Member.builder()
                .nickname(nickname)
                .profilePictureUrl(profilePictureUrl)
                .build();
    }
}
