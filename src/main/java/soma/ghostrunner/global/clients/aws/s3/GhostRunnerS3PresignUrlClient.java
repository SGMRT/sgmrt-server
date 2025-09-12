package soma.ghostrunner.global.clients.aws.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class GhostRunnerS3PresignUrlClient {

    private static final Integer PRESIGNED_URL_VALID_MINUTES = 10;
    public static final Set<String> PROFILE_IMAGE_ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg");

    private final String s3Bucket;
    private final String memberProfileDirectory;

    private S3Presigner s3Presigner;

    public GhostRunnerS3PresignUrlClient(@Value("${s3.bucket}") String s3Bucket,
                                         @Value("${s3.member-directory}") String memberProfileDirectory,
                                         S3Presigner s3Presigner) {
        this.s3Bucket = s3Bucket;
        this.memberProfileDirectory = memberProfileDirectory;
        this.s3Presigner = s3Presigner;
    }

    public PresignUrlDto getMemberProfilePresignUrl(String originalFilename) {
        String fileExtension = getFileExtension(originalFilename);
        checkFileExtensionAllowed(fileExtension);

        String objectKey = memberProfileDirectory + "/" + UUID.randomUUID() + "_" + originalFilename;
        PutObjectRequest putObjectRequest = createPutObjectRequest(objectKey);
        PutObjectPresignRequest putObjectPresignRequest = createPutObjectPresignRequest(putObjectRequest);

        String url = requestPresignUrlToS3(putObjectPresignRequest);

        return PresignUrlDto.of(url, objectKey);
    }

    private String requestPresignUrlToS3(PutObjectPresignRequest putObjectPresignRequest) {
        PresignedPutObjectRequest presignPutObject = s3Presigner.presignPutObject(putObjectPresignRequest);
        String url = presignPutObject.url().toString();
        log.info("Presign URL 요청 성공: {}", url);
        return url;
    }

    private String getFileExtension(String filename) {
        try {
            return filename.substring(filename.lastIndexOf("."));
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("프로필 사진 확장자 형식이 잘못된 경우");
        }
    }

    private void checkFileExtensionAllowed(String fileExtension) {
        if (!PROFILE_IMAGE_ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new IllegalArgumentException("허용되지 않은 프로필 사진 확장자");
        }
    }

    private PutObjectRequest createPutObjectRequest(String objectKey) {
        return PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(objectKey)
                .build();
    }

    private PutObjectPresignRequest createPutObjectPresignRequest(PutObjectRequest putObjectRequest) {
        return PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_VALID_MINUTES))
                .putObjectRequest(putObjectRequest)
                .build();
    }

}
