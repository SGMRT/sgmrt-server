package soma.ghostrunner.domain.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.notification.api.dto.NotificationBroadcastRequest;
import soma.ghostrunner.domain.notification.application.DeviceService;
import soma.ghostrunner.domain.notification.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.domain.notification.api.dto.NotificationSendRequest;
import soma.ghostrunner.domain.notification.api.dto.PushTokenSaveRequest;
import soma.ghostrunner.domain.notification.application.NotificationService;
import soma.ghostrunner.domain.notification.exception.IllegalNotificationBroadcastException;
import soma.ghostrunner.global.common.validator.auth.AdminOnly;
import soma.ghostrunner.global.common.versioning.SemanticVersion;
import soma.ghostrunner.global.common.versioning.VersionRange;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationApi {
    private final NotificationService notificationService;
    private final DeviceService deviceService;

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PostMapping("/v1/members/{memberUuid}/devices")
    public void registerDeviceInfo(@PathVariable String memberUuid,
                                   @Valid @RequestBody DeviceRegistrationRequest request,
                                   @AuthenticationPrincipal JwtUserDetails userDetails) {
        deviceService.registerDevice(memberUuid, request);
    }

    @Deprecated(since = "POST /v1/members/{memberUuid}/devices 로 대체 (하위 버전 클라이언트 호환을 위해 남겨둠)")
    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PostMapping("/v1/member/{memberUuid}/push-token")
    public void updatePushToken(@PathVariable("memberUuid") String memberUuid,
                                @Valid @RequestBody PushTokenSaveRequest request,
                                @AuthenticationPrincipal JwtUserDetails userDetails) {
        deviceService.saveMemberPushToken(memberUuid, request.getPushToken());
    }


    /* * * * * * * **
     *  어드민용 API  *
     * * * * * * * **/

    @Operation(summary = "푸시알림 전송 (어드민 전용)")
    @AdminOnly
    @PostMapping("/v1/admin/notifications")
    public void sendNotification(@RequestBody NotificationSendRequest request) {
        notificationService.sendPushNotification(request.getUserIds(), request.getTitle(), request.getBody(), request.getData(), VersionRange.ALL_VERSIONS);
    }

    @Operation(summary = "푸시알림 브로드캐스트 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/v1/admin/notifications/broadcast", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String broadcastNotification(@ModelAttribute NotificationBroadcastRequest request) {
        try {
            VersionRange versionRange = determineVersionRange(request);
            Map<String, Object> dataMap = request.dataMap();
            int count = notificationService.broadcastPushNotification(request.getTitle(), request.getBody(), request.dataMap(), versionRange);
            return "Sent " + count + " push notifications.";
        } catch (Exception e) {
            throw new IllegalNotificationBroadcastException(ErrorCode.ILLEGAL_NOTIFICATION_BROADCAST);
        }
    }

    private VersionRange determineVersionRange(NotificationBroadcastRequest request) {
        SemanticVersion.of(request.getVersion()); // version 포맷 검증
        if (request.getVersionRange() == NotificationBroadcastRequest.RangeType.ALL_VERSIONS) {
            if (StringUtils.hasText(request.getVersion())) {
                throw new IllegalNotificationBroadcastException(ErrorCode.VERSION_NOT_REQUIRED_FOR_BROADCAST);
            }
            return VersionRange.ALL_VERSIONS;
        }
        if (!StringUtils.hasText(request.getVersion())) {
            throw new IllegalNotificationBroadcastException(ErrorCode.VERSION_REQUIRED_FOR_BROADCAST);
        }
        return switch (request.getVersionRange()) {
            case AT_LEAST -> VersionRange.atLeast(request.getVersion());
            case EXACTLY -> VersionRange.exactly(request.getVersion());
            case AT_MOST -> VersionRange.atMost(request.getVersion());
            default -> throw new IllegalNotificationBroadcastException(ErrorCode.ILLEGAL_NOTIFICATION_BROADCAST);
        };
    }
}
