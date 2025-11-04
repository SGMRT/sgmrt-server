package soma.ghostrunner.domain.notification.application;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;
import soma.ghostrunner.domain.notification.domain.Device;
import soma.ghostrunner.domain.notification.domain.event.NotificationCommand;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final PushNotificationSqsSender sqsSender;
    private final DeviceService deviceService;

    public void sendPushNotification(NotificationCommand command) {
        sendPushNotification(
                command.memberIds(),
                command.title(),
                command.body(),
                command.data()
        );
    }

    @SentrySpan
    public void sendPushNotification(List<Long> userIds, String title, String body, Map<String, Object> data) {
        List<Device> devices = deviceService.findDevicesByMemberIds(userIds);
        List<PushMessageDto> pushMessages = devices.stream()
                .map(device -> new PushMessageDto(device.getToken(), title, body, data, null))
                .toList();
        sqsSender.sendMany(pushMessages);
        log.info("{}개의 푸시 알림 대기열 등록 완료 (푸시 알림: title={}, body={}, data={})", pushMessages.size(), title, body, data);
    }

}
