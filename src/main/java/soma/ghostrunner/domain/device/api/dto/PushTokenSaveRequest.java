package soma.ghostrunner.domain.device.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PushTokenSaveRequest {
    @NotBlank
    String pushToken;
}
