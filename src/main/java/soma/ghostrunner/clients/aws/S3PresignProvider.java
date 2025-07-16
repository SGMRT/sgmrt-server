package soma.ghostrunner.clients.aws;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class S3PresignProvider {
    private final S3Presigner s3Presigner;
    @Value("${s3.telemetry-bucket-name}")
    private String bucketName;

    public String generatePresignedPutUrl(String objectKey, String contentType, Duration expiration) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(req -> req
                .putObjectRequest(putObjectRequest)
                .signatureDuration(expiration));

        return presignedRequest.url().toString();
    }

}
