package soma.ghostrunner.domain.notification.application;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.device.application.DeviceService;
import soma.ghostrunner.domain.notification.application.dto.PushMessage;
import soma.ghostrunner.domain.device.domain.Device;
import soma.ghostrunner.domain.notification.application.dto.PushContent;
import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final PushSqsSender sqsSender;
    private final DeviceService deviceService;

    public void push(Long recipientId, PushContent content) {
        push(List.of(recipientId), content.title(), content.body(), content.data(), content.versionRange());
    }

    @SentrySpan
    public int broadcast(PushContent content) {
        // todo 회원 2천명인데 Device 페이징 조회해서 push 여러 번 호출해야겠지?
        List<Device> devices = deviceService.findDevicesByAppVersions(content.versionRange());
        return publishPushMessage(content.title(), content.body(), content.data(), devices);
    }

    @SentrySpan
    public int push(List<Long> userIds, String title, String body, Map<String, Object> data, VersionRange versionRange) {
        List<Device> devices = deviceService.findDevicesByMemberIdsAndAppVersions(userIds, versionRange);
        List<Device> pushAllowedDevices = filterPushAllowedDevices(devices);
        return publishPushMessage(title, body, data, pushAllowedDevices);
    }

    private int publishPushMessage(String title, String body, Map<String, Object> data, List<Device> devices) {
        List<PushMessage> pushMessages = devices.stream()
                .map(device -> new PushMessage(List.of(device.getToken()), title, body, data))
                .toList();
        sqsSender.sendMany(pushMessages);
        log.info("{}개의 푸시 알림 대기열 등록 완료 (푸시 알림: title={}, body={}, data={})", pushMessages.size(), title, body, data);
        return pushMessages.size();
    }

    private List<Device> filterPushAllowedDevices(List<Device> devices) {
        return devices.stream()
                .filter(device -> device.getToken() != null && !device.getToken().isBlank())
                .toList();
    }

}
