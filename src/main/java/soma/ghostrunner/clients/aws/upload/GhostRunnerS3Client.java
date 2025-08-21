package soma.ghostrunner.clients.aws.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import soma.ghostrunner.domain.running.application.dto.CheckpointDto;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.ExternalIOException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GhostRunnerS3Client {

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String s3Bucket;

    @Value("${s3.running-directory}")
    private String runningDirectory;

    @Value("${s3.course-directory}")
    private String courseDirectory;

    @Value("${s3.course-checkpoint-directory}")
    private String courseCheckpointDirectory;

    @Value("${s3.member-directory}")
    private String memberDirectory;

    public String uploadInterpolatedTelemetry(List<TelemetryDto> telemetryDtos, String memberUuid) {
        return uploadObjectList(telemetryDtos, runningDirectory, memberUuid);
    }

    public String uploadSimplifiedTelemetry(List<CoordinateDto> coordinateDtos, String memberUuid) {
        return uploadObjectList(coordinateDtos, courseDirectory, memberUuid);
    }

    public String uploadCourseCheckpoint(List<CheckpointDto> checkpointDtos, String memberUuid) {
        return uploadObjectList(checkpointDtos, courseCheckpointDirectory, memberUuid);
    }

    private String uploadObjectList(List<?> objectList, String directory, String memberUuid) {
        String fileName = String.format("%s/%s/%s.jsonl", directory, memberUuid, UUID.randomUUID());

        try {
            String jsonlContent = objectList.stream()
                    .map(item -> {
                        try {
                            return objectMapper.writeValueAsString(item);
                        } catch (JsonProcessingException e) {
                            log.error("객체 JSON 직렬화 실패: {}", item, e);
                            throw new RuntimeException("객체를 JSON으로 변환하는 중 오류가 발생했습니다.", e);
                        }
                    })
                    .collect(Collectors.joining("\n"));

            byte[] contentBytes = jsonlContent.getBytes(StandardCharsets.UTF_8);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(fileName)
                    .contentType("application/jsonl")
                    .contentLength((long) contentBytes.length)
                    .build();

            log.info("S3에 리스트 JSONL 업로드 중.. 파일 이름: {}, 크기: {} bytes", fileName, contentBytes.length);
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(contentBytes));

            log.info("S3에 리스트 JSONL 업로드 성공. 파일: {}", fileName);
            return getS3FileUrl(fileName);
        } catch (Exception e) {
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에 리스트를 JSONL로 업로드하는데 실패했습니다.");
        }
    }

    public String uploadRawTelemetry(MultipartFile rawTelemetry, String memberUuid) {
        String fileName = String.format("%s/%s/%s.jsonl", runningDirectory, memberUuid, UUID.randomUUID());

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(fileName)
                    .contentType("application/jsonl")
                    .contentLength(rawTelemetry.getSize())
                    .build();

            log.info("S3에 MultipartFile JSONL 업로드 중.. 파일 이름: {}, 크기: {} bytes", fileName, rawTelemetry.getSize());
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(rawTelemetry.getInputStream(), rawTelemetry.getSize()));

            log.info("S3에 MultipartFile JSONL 업로드 성공. 파일: {}", fileName);
            return getS3FileUrl(fileName);
        } catch (Exception e) {
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에 MultipartFile(JSONL)을 업로드하는데 실패했습니다.");
        }
    }

    public String uploadRunningCaptureImage(MultipartFile runningCaptureImage, String memberUuid) {
        return uploadImageFile(runningCaptureImage, runningDirectory, memberUuid);
    }

    public String uploadMemberProfileImage(MultipartFile memberProfileImage, String memberUuid) {
        return uploadImageFile(memberProfileImage, memberDirectory, memberUuid);
    }

    private String uploadImageFile(MultipartFile imageFile, String directory, String memberUuid) {
        String originalFilename = imageFile.getOriginalFilename();
        String extension = ".jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String fileName = String.format("%s/%s/%s%s", directory, memberUuid, UUID.randomUUID(), extension);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(fileName)
                    .contentType(imageFile.getContentType())
                    .contentLength(imageFile.getSize())
                    .build();

            log.info("S3에 이미지 업로드 중.. 파일 이름: {}, 크기: {} bytes", fileName, imageFile.getSize());
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(imageFile.getInputStream(), imageFile.getSize()));

            log.info("S3에 이미지 업로드 성공. 파일: {}", fileName);
            return getS3FileUrl(fileName);
        } catch (Exception e) {
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에 이미지를 업로드하는데 실패했습니다.");
        }
    }

    private String getS3FileUrl(String fileKey) {
        return s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(s3Bucket).key(fileKey).build()).toString();
    }

}
