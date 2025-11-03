package soma.ghostrunner.domain.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class DeviceRegistrationRequest {
    @NotBlank
    private String deviceUuid;

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
