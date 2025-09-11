package soma.ghostrunner.domain.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PushTokenSaveRequest {
    String pushToken;
}
