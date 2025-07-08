package soma.ghostrunner.clients.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.ExternalIOException;
import soma.ghostrunner.global.common.error.exception.ParsingException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryClient {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${s3.telemetry-bucket-name}")
    private String telemetryBucket;

    @Value("${s3.telemetry-directory-name}")
    private String telemetryDirectory;

    // 업로드
    public String uploadTelemetries(String telemetries, Long memberId) {

        // TODO : 파일 이름에서 유저의 UUID 필드로 저장되도록 수정
        String fileName = telemetryDirectory + "/" + memberId + "/" + UUID.randomUUID() + ".jsonl";

        // 업로드할 객체
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(telemetryBucket)
                .key(fileName)
                .contentType("application/jsonl")
                .contentLength((long) telemetries.getBytes(StandardCharsets.UTF_8).length)
                .build();

        // 업로드
        try{
            log.info("Attempting to upload telemetry file. FileName: {}, Size: {} bytes", fileName, telemetries.getBytes(StandardCharsets.UTF_8).length);
            s3Client.putObject(putObjectRequest, RequestBody.fromString(telemetries));
        } catch (Exception e) {
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3와 통신에 실패했습니다.");
        }

        log.info("Successfully uploaded file to S3: {}", fileName);
        return s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(telemetryBucket).key(fileName).build()).toString();
    }

    // 다운로드
    public List<String> downloadTelemetryFromUrl(String s3Url) {
        String fileName = extractFileNameFromUrl(s3Url);
        return downloadTelemetry(fileName);
    }

    // URL -> 파일명 추출
    private String extractFileNameFromUrl(String s3Url) {
        try {
            int bucketEndIndex = s3Url.indexOf(".s3.");
            if (bucketEndIndex == -1) {
                throw new IllegalArgumentException("올바르지 않은 S3 URL 형식입니다: " + s3Url);
            }

            int pathStartIndex = s3Url.indexOf("/", bucketEndIndex + 4);
            if (pathStartIndex == -1) {
                throw new IllegalArgumentException("S3 URL에서 파일 경로를 찾을 수 없습니다: " + s3Url);
            }

            return s3Url.substring(pathStartIndex + 1);
        } catch (Exception e) {
            log.error("S3 URL({})에서 파일명 추출에 실패했습니다.", s3Url, e);
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "S3 URL에서 파일명을 추출하는 중 오류가 발생했습니다");
        }
    }

    private List<String> downloadTelemetry(String fileName) {
        try {
            // S3 스트림 연결
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(telemetryBucket)
                    .key(fileName)
                    .build();
            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getObjectRequest);

            // 읽기
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3ObjectStream, StandardCharsets.UTF_8))) {
                List<String> result = reader.lines().collect(Collectors.toList());
                log.info("S3로 부터 다운로드 성공: {}", fileName);
                System.out.println(result.size());
                for (String line : result) {
                    System.out.println(line);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("S3로 부터 다운로드 실패: {}", fileName, e);
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에서 텔레메트리 데이터를 다운로드하는 중 오류가 발생했습니다.");
        }
    }

}
