package soma.ghostrunner.domain.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class DeviceRegistrationRequest {
    @NotBlank
    private String deviceUuid;

    @NotBlank
    private String appVersion;

    @NotBlank
    private String pushToken;

    private String osName;

    private String osVersion;

    private String modelName;

}
