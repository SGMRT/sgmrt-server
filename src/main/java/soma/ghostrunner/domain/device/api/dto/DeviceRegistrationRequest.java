package soma.ghostrunner.domain.device.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@ToString
@EqualsAndHashCode
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
