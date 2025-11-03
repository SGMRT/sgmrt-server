package soma.ghostrunner.domain.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import soma.ghostrunner.domain.notification.application.DeviceService;
import soma.ghostrunner.domain.notification.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.domain.notification.api.dto.NotificationSendRequest;
import soma.ghostrunner.domain.notification.api.dto.PushTokenSaveRequest;
import soma.ghostrunner.domain.notification.application.NotificationService;
import soma.ghostrunner.global.common.validator.auth.AdminOnly;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

@RestController
@RequiredArgsConstructor
public class NotificationApi {
    private final NotificationService notificationService;
    private final DeviceService deviceService;

    @Operation(summary = "푸시알람 전송 (어드민 전용)")
    @AdminOnly
    @PostMapping("/v1/admin/notifications")
    public void sendNotification(@RequestBody NotificationSendRequest request) {
        notificationService.sendPushNotification(request.getUserIds(), request.getTitle(), request.getBody(), request.getData());
    }

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PostMapping("/v1/{memberUuid}/devices")
    public void registerDeviceInfo(@PathVariable String memberUuid,
                                   @Valid @RequestBody DeviceRegistrationRequest request,
                                   @AuthenticationPrincipal JwtUserDetails userDetails) {
        deviceService.registerDevice(memberUuid, request);
    }

    @Deprecated(since = "POST /v1/{memberUuid}/devices 로 대체 (하위 버전 클라이언트 호환을 위해 남겨둠)")
    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PostMapping("/v1/member/{memberUuid}/push-token")
    public void updatePushToken(@PathVariable("memberUuid") String memberUuid,
                                @Valid @RequestBody PushTokenSaveRequest request,
                                @AuthenticationPrincipal JwtUserDetails userDetails) {
        deviceService.saveMemberPushToken(memberUuid, request.getPushToken());
    }

}
