package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class RunningDataUrlsDto {

    private String rawTelemetryUrl;
    private String interpolatedTelemetryUrl;
    @Setter
    private String simplifiedPathSavedUrl;
    private String screenShotUrl;

}
