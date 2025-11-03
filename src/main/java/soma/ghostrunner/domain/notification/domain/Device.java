package soma.ghostrunner.domain.notification.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "push_token")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE push_token SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(name = "installation_id", unique = true, nullable = false)
    private String uuid;

    @Column(nullable = false)
    private String appVersion = DeviceConstants.APP_VERSION_DEFAULT;

    @Column(nullable = false)
    private String osName = DeviceConstants.OS_NAME_DEFAULT;

    @Column(nullable = false)
    private String osVersion = DeviceConstants.OS_VERSION_DEFAULT;

    @Column(nullable = false)
    private String modelName = DeviceConstants.OS_MODEL_DEFAULT;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    public Device(Member member, String token) {
        this.member = member;
        this.token = token;
    }

    @Builder
    private Device(Member member, String token, String uuid, String appVersion,
                   String osName, String osVersion, String modelName) {
        this.member = member;
        this.token = token;
        this.uuid = uuid;
        this.appVersion = appVersion;
        this.osName = osName;
        this.osVersion = osVersion;
        this.modelName = modelName;
    }

    public void updateMember(Member member) {
        this.member = member;
    }

    public void updateToken(String token) {
        if( !( token.startsWith("ExponentPushToken[") && token.endsWith("]") )) {
            throw new IllegalArgumentException("올바른 Push Token 방식이 아닙니다: " + token);
        }
        this.token = token;
    }

}
