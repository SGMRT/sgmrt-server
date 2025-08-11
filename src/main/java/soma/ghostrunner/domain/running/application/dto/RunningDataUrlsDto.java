package soma.ghostrunner.domain.running.application.dto;

public record RunningDataUrlsDto(
        String rawTelemetrySavedUrl, String interpolatedTelemetrySavedUrl,
        String simplifiedPathSavedUrl, String screenShotSavedUrl){
}
