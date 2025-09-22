package soma.ghostrunner.domain.running.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
public class RunningDataUrlsDto {

    private String rawTelemetryUrl;
    private String interpolatedTelemetryUrl;
    @Setter
    private String simplifiedPathSavedUrl;
    @Setter
    private String checkpointUrl;
    private String screenShotUrl;

    public RunningDataUrlsDto(String rawTelemetryUrl, String interpolatedTelemetryUrl,
                              String simplifiedPathSavedUrl, String checkpointUrl, String screenShotUrl) {
        this.rawTelemetryUrl = rawTelemetryUrl;
        this.interpolatedTelemetryUrl = interpolatedTelemetryUrl;
        this.simplifiedPathSavedUrl = simplifiedPathSavedUrl;
        this.checkpointUrl = checkpointUrl;
        this.screenShotUrl = screenShotUrl;
    }


    public RunningDataUrlsDto(String rawUrl, String interpolatedUrl, String screenShotUrl) {
        this.rawTelemetryUrl = rawUrl;
        this.interpolatedTelemetryUrl = interpolatedUrl;
        this.screenShotUrl = screenShotUrl;
    }

}
