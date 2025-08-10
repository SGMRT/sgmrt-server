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

    @Column(name = "raw_telemetry_saved_url")
    private String rawTelemetrySavedUrl;

    @Column(name = "interpolated_telemetry_saved_url", nullable = false, length = 2048)
    private String interpolatedTelemetrySavedUrl;

    @Column(name = "screen_shot_saved_url")
    private String screenShotSavedUrl;

    @Builder(access = AccessLevel.PRIVATE)
    public RunningDataUrls(String rawTelemetrySavedUrl,
                           String interpolatedTelemetrySavedUrl, String screenshotSavedUrl) {
        this.rawTelemetrySavedUrl = rawTelemetrySavedUrl;
        this.interpolatedTelemetrySavedUrl = interpolatedTelemetrySavedUrl;
        this.screenShotSavedUrl = screenshotSavedUrl;
    }

    static RunningDataUrls of(String rawTelemetrySavedUrl,
                              String interpolatedTelemetrySavedUrl, String screenshotSavedUrl) {
        return RunningDataUrls.builder()
                .rawTelemetrySavedUrl(rawTelemetrySavedUrl)
                .interpolatedTelemetrySavedUrl(interpolatedTelemetrySavedUrl)
                .screenshotSavedUrl(screenshotSavedUrl)
                .build();
    }

}
