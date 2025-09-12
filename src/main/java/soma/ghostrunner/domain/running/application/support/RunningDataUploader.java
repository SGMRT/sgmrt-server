package soma.ghostrunner.domain.running.application.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.domain.path.SimplifiedPath;

@Component
@RequiredArgsConstructor
public class RunningDataUploader {

    private final GhostRunnerS3Client ghostRunnerS3Client;

    public RunningDataUrlsDto uploadAll(
            MultipartFile rawTelemetry, TelemetryStatistics processedTelemetries,
            SimplifiedPath simplifiedPath, MultipartFile screenShotImage, String memberUuid) {

        String rawUrl = uploadRawTelemetry(rawTelemetry, memberUuid);
        String interpolatedUrl = uploadInterpolatedTelemetry(processedTelemetries, memberUuid);
        String simplifiedUrl = ghostRunnerS3Client.uploadSimplifiedTelemetry(simplifiedPath.simplifiedCoordinates(), memberUuid);
        String checkpointUrl = ghostRunnerS3Client.uploadCourseCheckpoint(simplifiedPath.checkpoints(), memberUuid);

        String screenShotUrl = uploadScreenShotImage(screenShotImage, memberUuid);

        return new RunningDataUrlsDto(rawUrl, interpolatedUrl, simplifiedUrl, checkpointUrl, screenShotUrl);
    }

    private String uploadRawTelemetry(MultipartFile rawTelemetry, String memberUuid) {
        return ghostRunnerS3Client.uploadRawTelemetry(rawTelemetry, memberUuid);
    }

    private String uploadInterpolatedTelemetry(TelemetryStatistics processedTelemetries, String memberUuid) {
        return ghostRunnerS3Client.uploadInterpolatedTelemetry(processedTelemetries.relativeTelemetries(), memberUuid);
    }

    private String uploadScreenShotImage(MultipartFile screenShotImage, String memberUuid) {
        return (screenShotImage != null)
                ? uploadRunningScreenShotImage(memberUuid, screenShotImage)
                : null;
    }

    private String uploadRunningScreenShotImage(String memberUuid, MultipartFile screenShotImage) {
        return ghostRunnerS3Client.uploadRunningCaptureImage(screenShotImage, memberUuid);
    }

    public RunningDataUrlsDto uploadAll(
            MultipartFile rawTelemetry, TelemetryStatistics processedTelemetries,
            MultipartFile screenShotImage, String memberUuid) {

        String rawUrl = uploadRawTelemetry(rawTelemetry, memberUuid);
        String interpolatedUrl = uploadInterpolatedTelemetry(processedTelemetries, memberUuid);

        String screenShotUrl = uploadScreenShotImage(screenShotImage, memberUuid);

        return new RunningDataUrlsDto(rawUrl, interpolatedUrl, screenShotUrl);
    }

}
