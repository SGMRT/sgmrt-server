package soma.ghostrunner.domain.member.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.common.document.TestOnly;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "member")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE member SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private RoleType roleType;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "member")
    private List<Running> runs = new ArrayList<>();

    @Builder
    private Member(String nickname, String profilePictureUrl,
                  MemberBioInfo bioInfo, LocalDateTime lastLoginAt) {
        this.nickname = nickname;
        this.profilePictureUrl = profilePictureUrl;
        this.uuid = UUID.randomUUID().toString();
        this.bioInfo = bioInfo == null ? new MemberBioInfo(null, null, null, null) : bioInfo;
        this.lastLoginAt = lastLoginAt;
        this.roleType = RoleType.USER;
    }

    public static Member of(String nickname, String profilePictureUrl) {
        return Member.builder()
                .nickname(nickname)
                .bioInfo(new MemberBioInfo(null, null, null, null))
                .profilePictureUrl(profilePictureUrl)
                .build();
    }

    public void updateNickname(String nickname) {
        if(nickname == null) throw new IllegalArgumentException("nickname cannot be null");
        if(nickname.trim().isEmpty()) throw new IllegalArgumentException("nickname cannot be empty");
        if(nickname.length() > 10) throw new IllegalArgumentException("nickname cannot be longer than 10 characters");
        this.nickname = nickname;
    }

    public void updateBioInfo(Gender gender, Integer age, Integer weight, Integer height) {
        // gender, weight, height는 null check X -> 요청 시 null로 변경 가능
        if(weight != null && weight < 0) throw new IllegalArgumentException("weight cannot be negative");
        if(height != null && height < 0) throw new IllegalArgumentException("height cannot be negative");
        if(age != null && age < 0) throw new IllegalArgumentException("age cannot be negative");
        this.bioInfo = new MemberBioInfo(gender, age, weight, height);
    }

    public void updateProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    @TestOnly
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String toStringForPacemakerPrompt(int vdot, int condition) {

        String conditionStr = switch (condition) {
            case 1 -> "매우 안좋음";
            case 2 -> "안좋음";
            case 3 -> "보통";
            case 4 -> "좋음";
            case 5 -> "매우 좋음";
            default -> throw new IllegalArgumentException("유효하지 않은 컨디션 값입니다.");
        };

        return String.format(
                "{\n" +
                    "\t    \"age\": %d,\n" +
                    "\t    \"gender\": \"%s\",\n" +
                    "\t    \"weight\": %d,\n" +
                    "\t    \"height\": %d,\n" +
                    "\t    \"vdot\": %d,\n" +
                    "\t    \"condition\": \"%s\"\n" +
                "\t}",
                bioInfo.getAge(), bioInfo.getGender(), bioInfo.getWeight(), bioInfo.getHeight(), vdot, conditionStr
        );
    }

    public boolean isAdmin() {
        return this.roleType == RoleType.ADMIN;
    }

}
