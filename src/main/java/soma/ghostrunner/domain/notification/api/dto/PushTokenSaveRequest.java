package soma.ghostrunner.domain.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PushTokenSaveRequest {
    @NotBlank
    String pushToken;
}
