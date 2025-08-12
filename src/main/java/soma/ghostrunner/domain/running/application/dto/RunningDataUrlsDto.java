package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class RunningDataUrlsDto {

    private String rawTelemetrySavedUrl;
    private String interpolatedTelemetrySavedUrl;
    @Setter
    private String simplifiedPathSavedUrl;
    private String screenShotSavedUrl;

}
