package soma.ghostrunner.domain.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.notification.api.dto.PushBroadcastRequest;
import soma.ghostrunner.domain.notification.api.dto.PushSendRequest;
import soma.ghostrunner.domain.notification.application.PushService;
import soma.ghostrunner.domain.notification.application.dto.PushContent;
import soma.ghostrunner.domain.notification.exception.IllegalNotificationBroadcastException;
import soma.ghostrunner.global.common.validator.auth.AdminOnly;
import soma.ghostrunner.global.common.versioning.SemanticVersion;
import soma.ghostrunner.global.common.versioning.VersionRange;
import soma.ghostrunner.global.error.ErrorCode;

@RestController
@RequiredArgsConstructor
public class NotificationApi {
    private final PushService pushService;

    @PostMapping("/v1/push/{pushId}")
    public void markPushNotificationAsRead(@PathVariable("pushId") Long pushId) {
        pushService.markAsRead(pushId);
    }

    @Operation(summary = "푸시알림 전송 (어드민 전용)")
    @AdminOnly
    @PostMapping("/v1/admin/push")
    public void sendNotification(@RequestBody PushSendRequest request) {
        for (var recipientId : request.getUserIds()) {
            pushService.push(recipientId, PushContent.of(request.getTitle(), request.getBody(), request.getData(), VersionRange.ALL_VERSIONS));
        }
    }

    @Operation(summary = "푸시알림 브로드캐스트 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/v1/admin/push/broadcast", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String broadcastNotification(@ModelAttribute PushBroadcastRequest request) {
        try {
            VersionRange versionRange = determineVersionRange(request);
            int count = pushService.broadcast(PushContent.of(request.getTitle(), request.getBody(), request.dataMap(), versionRange));
            return "Sent " + count + " push notifications.";
        } catch (Exception e) {
            throw new IllegalNotificationBroadcastException(ErrorCode.ILLEGAL_NOTIFICATION_BROADCAST);
        }
    }

    private VersionRange determineVersionRange(PushBroadcastRequest request) {
        SemanticVersion.of(request.getVersion()); // version 포맷 검증
        if (request.getVersionRange() == PushBroadcastRequest.RangeType.ALL_VERSIONS) {
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
