package soma.ghostrunner.domain.notification.application;

import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;

public interface PushMessagePublisher {
    void send(PushMessageDto message);
}
