package soma.ghostrunner.domain.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeviceRegistrationRequest {
    @NotBlank
    private String installationId;

    @NotBlank
    private String appVersion;

    @NotBlank
    private String pushToken;

    @NotBlank
    private String osName;

    @NotBlank
    private String osVersion;

    @NotBlank
    private String modelName;

}
