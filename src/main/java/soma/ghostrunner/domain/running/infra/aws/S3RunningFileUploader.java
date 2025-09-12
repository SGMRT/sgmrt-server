package soma.ghostrunner.domain.running.infra.aws;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.global.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.running.domain.path.Checkpoint;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.Telemetry;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3RunningFileUploader implements RunningFileUploader {

    private final GhostRunnerS3Client s3Client;

    @Value("${s3.running-directory}")
    private String runningDirectory;

    @Value("${s3.course-directory}")
    private String courseDirectory;

    @Override
    public String uploadRawTelemetry(MultipartFile rawTelemetry, String memberUuid) {
        String fileName = toJsonlFileName(runningDirectory, memberUuid);
        return s3Client.uploadMultipartFile(rawTelemetry, fileName);
    }

    @Override
    public String uploadInterpolatedTelemetry(List<Telemetry> telemetries, String memberUuid) {
        String fileName = toJsonlFileName(runningDirectory, memberUuid);
        return s3Client.uploadObjectList(telemetries, fileName);
    }

    @Override
    public String uploadSimplifiedCoordinates(List<Coordinates> coordinates, String memberUuid) {
        String fileName = toJsonlFileName(courseDirectory, memberUuid);
        return s3Client.uploadObjectList(coordinates, fileName);
    }

    @Override
    public String uploadCheckpoints(List<Checkpoint> checkpoints, String memberUuid) {
        String fileName = toJsonlFileName(courseDirectory, memberUuid);
        return s3Client.uploadObjectList(checkpoints, fileName);
    }

    private String toJsonlFileName(String directory, String memberUuid) {
        return String.format("%s/%s/%s.jsonl", directory, memberUuid, UUID.randomUUID());
    }

    @Override
    public String uploadRunningCaptureImage(MultipartFile runningCaptureImage, String memberUuid) {

        String originalFilename = runningCaptureImage.getOriginalFilename();
        String extension = ".jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String fileName = String.format("%s/%s/%s%s", runningDirectory, memberUuid, UUID.randomUUID(), extension);
        return s3Client.uploadMultipartFile(runningCaptureImage, fileName);
    }

}
