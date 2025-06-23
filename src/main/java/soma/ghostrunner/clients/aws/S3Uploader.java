package soma.ghostrunner.clients.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3Uploader {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${s3.telemetry-bucket-name}")
    private String telemetryBucket;

    @Value("${s3.telemetry-directory-name}")
    private String telemetryDirectory;

    public String uploadTelemetry(List<TelemetryCommand> telemetries, Long memberId) {
        // (4) .jsonl 형식의 문자열 생성
        String jsonlContent = telemetries.stream()
                .map(this::convertToJson)
                .collect(Collectors.joining("\n"));

        // 파일 이름
        String fileName = telemetryDirectory + "/" + memberId + "/" + UUID.randomUUID() + ".jsonl";

        // 업로드할 요청 객체
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(telemetryBucket)
                .key(fileName)
                .contentType("application/jsonl") // 파일 타입 지정
                .contentLength((long) jsonlContent.getBytes(StandardCharsets.UTF_8).length)
                .build();

        // 업로드
        s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonlContent));
        log.info("Successfully uploaded file to S3: {}", fileName);

        // URL
        return s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(telemetryBucket).key(fileName).build()).toString();
    }

    // 객체 -> JSON 문자열
    private String convertToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("S3 업로드를 위한 JSON 변환 중 오류가 발생했습니다.", e);
        }
    }

}
