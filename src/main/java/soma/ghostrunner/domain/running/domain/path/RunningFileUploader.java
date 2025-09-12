package soma.ghostrunner.domain.running.domain.path;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RunningFileUploader {

    String uploadRawTelemetry(MultipartFile rawTelemetry, String memberUuid);

    String uploadInterpolatedTelemetry(List<Telemetry> telemetries, String memberUuid);

    String uploadSimplifiedCoordinates(List<Coordinates> coordinates, String memberUuid);

    String uploadCheckpoints(List<Checkpoint> checkpoints, String memberUuid);

    String uploadRunningCaptureImage(MultipartFile runningCaptureImage, String memberUuid);

}
