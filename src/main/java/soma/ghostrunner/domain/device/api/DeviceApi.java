package soma.ghostrunner.domain.device.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import soma.ghostrunner.domain.device.application.DeviceService;
import soma.ghostrunner.domain.device.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.domain.device.api.dto.PushTokenSaveRequest;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

@Controller
@RequiredArgsConstructor
public class DeviceApi {
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

}
