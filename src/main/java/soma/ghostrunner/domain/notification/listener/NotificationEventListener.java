package soma.ghostrunner.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ApplicationEventPublisher applicationEventPublisher;
    // todo 알람 기획 구체화 시 구현 (외부 이벤트 listen -> NotificationEvent로 변환 후 publish)

}
