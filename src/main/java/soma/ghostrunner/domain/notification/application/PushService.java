package soma.ghostrunner.domain.notification.application;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.device.application.DeviceService;
import soma.ghostrunner.domain.notification.application.dto.PushMessage;
import soma.ghostrunner.domain.device.domain.Device;
import soma.ghostrunner.domain.notification.application.dto.PushContent;
import soma.ghostrunner.domain.notification.dao.PushHistoryRepository;
import soma.ghostrunner.domain.notification.domain.PushHistory;
import soma.ghostrunner.domain.notification.exception.PushHistoryNotFound;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final PushSqsSender sqsSender;
    private final DeviceService deviceService;
    private final PushHistoryRepository pushHistoryRepository;

    @SentrySpan
    public int push(Long recipientId, PushContent content) {
        List<Device> pushableDevices = deviceService.findDevicesByMemberIdsAndAppVersions(List.of(recipientId), content.versionRange())
                .stream()
                .filter(this::isPushAllowed)
                .toList();
        if(pushableDevices.isEmpty()) return 0;
        PushHistory history = pushHistoryRepository.save(PushHistory.of(recipientId, content.title(), content.body(), content.data()));
        content.data().put("id", history.getId());
        return publishPushMessage(content.title(), content.body(), content.data(), pushableDevices);
    }

    @SentrySpan
    public int broadcast(PushContent content) {
        // todo 회원 2천명인데 Device 페이징 조회해서 push 여러 번 호출해야겠지?
        List<Device> pushableDevices = deviceService.findDevicesByAppVersions(content.versionRange()).stream()
                .filter(this::isPushAllowed)
                .toList();
        return publishPushMessage(content.title(), content.body(), content.data(), pushableDevices);
    }

    @Transactional
    public void markAsRead(Long pushId) {
        PushHistory history = pushHistoryRepository.findById(pushId).orElseThrow(PushHistoryNotFound::new);
        history.markAsRead(LocalDateTime.now());
    }

    private int publishPushMessage(String title, String body, Map<String, Object> data, List<Device> devices) {
        List<PushMessage> pushMessages = devices.stream()
                .map(device -> new PushMessage(List.of(device.getToken()), title, body, data))
                .toList();
        sqsSender.sendMany(pushMessages);
        log.info("{}개의 푸시 알림 대기열 등록 완료 (푸시 알림: title={}, body={}, data={})", pushMessages.size(), title, body, data);
        return pushMessages.size();
    }

    private boolean isPushAllowed(Device device) {
        return device.getToken() != null && !device.getToken().isBlank();
    }

}
