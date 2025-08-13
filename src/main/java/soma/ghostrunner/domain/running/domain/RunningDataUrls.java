package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningDataUrls {

    @Column(name = "raw_telemetry_url")
    private String rawTelemetryUrl;

    @Column(name = "interpolated_telemetry_url")
    private String interpolatedTelemetryUrl;

    @Column(name = "screen_shot_url")
    private String screenShotUrl;

    @Builder(access = AccessLevel.PRIVATE)
    public RunningDataUrls(String rawTelemetryUrl,
                           String interpolatedTelemetryUrl, String screenShotUrl) {
        this.rawTelemetryUrl = rawTelemetryUrl;
        this.interpolatedTelemetryUrl = interpolatedTelemetryUrl;
        this.screenShotUrl = screenShotUrl;
    }

    static RunningDataUrls of(String rawTelemetryUrl,
                              String interpolatedTelemetryUrl, String screenShotUrl) {
        return RunningDataUrls.builder()
                .rawTelemetryUrl(rawTelemetryUrl)
                .interpolatedTelemetryUrl(interpolatedTelemetryUrl)
                .screenShotUrl(screenShotUrl)
                .build();
    }

}
