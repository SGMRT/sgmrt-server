package soma.ghostrunner.domain.device.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.Assert;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.device.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.common.versioning.SemanticVersion;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Entity
@Table(name = "push_token", indexes = {
    @Index(name = "idx_push_token_member_versions", columnList = "member_id, app_version_major, app_version_minor, app_version_patch"),
})
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    @Column(unique = false, nullable = false) // soft delete로 인해 unique=false 불가. 로직으로 중복 제거 필요
    private String token;

    @Builder.Default
    @Column(name = "uuid", unique = false, nullable = true) // 클라 하위호환성을 위해 nullable하게 설정
    private String uuid = UUID.randomUUID().toString();

    @Builder.Default
    @Embedded
    @AttributeOverrides({ // SemanticVersion 필드명에 "app_version" 접두어 추가
            @AttributeOverride(name = "major", column = @Column(name = "app_version_major")),
            @AttributeOverride(name = "minor", column = @Column(name = "app_version_minor")),
            @AttributeOverride(name = "patch", column = @Column(name = "app_version_patch"))
    })
    private SemanticVersion appVersion = DeviceConstants.APP_VERSION_DEFAULT;

    @Builder.Default
    @Column(name = "os_name", nullable = true)
    private String osName = DeviceConstants.OS_NAME_DEFAULT;

    @Builder.Default
    @Column(name = "os_version", nullable = true)
    private String osVersion = DeviceConstants.OS_VERSION_DEFAULT;

    @Builder.Default
    @Column(name = "model_name", nullable = true)
    private String modelName = DeviceConstants.OS_MODEL_DEFAULT;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    public static Device of(Member member, String token) {
        return Device.builder()
                .member(member)
                .token(token)
                .build();
    }

    public static Device of(Member member, String token, String uuid, SemanticVersion appVersion,
                            String osName, String osVersion, String modelName) {
        validatedEssentialFields(token, uuid);
        return Device.builder()
                .member(member)
                .token(token)
                .uuid(uuid)
                .appVersion(appVersion != null ? appVersion : DeviceConstants.APP_VERSION_DEFAULT)
                .osName(osName != null ? osName : DeviceConstants.OS_NAME_DEFAULT)
                .osVersion(osVersion != null ? osVersion : DeviceConstants.OS_VERSION_DEFAULT)
                .modelName(modelName != null ? modelName : DeviceConstants.OS_MODEL_DEFAULT)
                .build();
    }

    public Set<String> updateInfo(Member member, DeviceRegistrationRequest request) {
        var updatedFields = new HashSet<String>();
        if (updateMember(member)) {
            updatedFields.add("member");
        }
        if (updateToken(request.getPushToken())) {
            updatedFields.add("token");
        }
        if (updateAppVersion(SemanticVersion.of(request.getAppVersion()))) {
            updatedFields.add("appVersion");
        }
        if (updateOsName(request.getOsName())) {
            updatedFields.add("osName");
        }
        if (updateOsVersion(request.getOsVersion())) {
            updatedFields.add("osVersion");
        }
        if (updateModelName(request.getModelName())) {
            updatedFields.add("modelName");
        }
        return updatedFields;
    }

    // helper methods
    private static void validatePushTokenFormat(String pushToken) {
        Assert.notNull(pushToken, "푸쉬토큰은 null일 수 없습니다.");
        if( !( pushToken.startsWith("ExponentPushToken[") && pushToken.endsWith("]") )) {
            throw new IllegalArgumentException("올바른 푸쉬 토큰 방식이 아닙니다: " + pushToken);
        }
    }

    private static void validatedEssentialFields(String token, String uuid) {
        validatePushTokenFormat(token);
        Assert.notNull(uuid, "Device UUID는 null일 수 없습니다.");
    }

    private boolean updateMember(Member member) {
        if (this.member == null && member == null) {
            return false;
        }
        if (this.member != null && this.member.getId().equals(member.getId())) {
            return false;
        }
        this.member = member;
        return true;
    }

    private boolean updateToken(String token) {
        if (this.token.equals(token)) {
            return false;
        }
        validatePushTokenFormat(token);
        this.token = token;
        return true;
    }

    private boolean updateAppVersion(SemanticVersion appVersion) {
        if (this.appVersion.equals(appVersion)) {
            return false;
        }
        this.appVersion = appVersion;
        return true;
    }

    private boolean updateOsName(String osName) {
        if (this.osName.equals(osName)) {
            return false;
        }
        this.osName = osName;
        return true;
    }

    private boolean updateOsVersion(String osVersion) {
        if (this.osVersion.equals(osVersion)) {
            return false;
        }
        this.osVersion = osVersion;
        return true;
    }

    private boolean updateModelName(String modelName) {
        if (this.modelName.equals(modelName)) {
            return false;
        }
        this.modelName = modelName;
        return true;
    }

}
