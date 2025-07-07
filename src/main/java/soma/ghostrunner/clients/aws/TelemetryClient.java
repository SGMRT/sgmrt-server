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
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.ExternalIOException;
import soma.ghostrunner.global.common.error.exception.ParsingException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    public String uploadTelemetries(List<TelemetryDto> telemetries, Long memberId) {
        // json -> String
        String jsonTelemetries = telemetries.stream()
                .map(this::convertToJson)
                .collect(Collectors.joining("\n"));

        // 파일 이름
        String fileName = telemetryDirectory + "/" + memberId + "/" + UUID.randomUUID() + ".jsonl";

        // 업로드할 요청 객체
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(telemetryBucket)
                .key(fileName)
                .contentType("application/jsonl")    // 파일 타입 지정
                .contentLength((long) jsonTelemetries.getBytes(StandardCharsets.UTF_8).length)
                .build();

        // 업로드
        try{
            log.info("Uploading Telemetry Files : \n{}", jsonTelemetries);
            s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonTelemetries));
        } catch (Exception e) {
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3와 통신에 실패했습니다.");
        }
        log.info("Successfully uploaded file to S3: {}", fileName);

        // URL
        return s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(telemetryBucket).key(fileName).build()).toString();
    }

    // 다운로드
    public List<TelemetryDto> downloadTelemetryFromUrl(String s3Url) {
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

    private List<TelemetryDto> downloadTelemetry(String fileName) {
        try {
            // S3에서 객체 가져오기 요청 생성
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(telemetryBucket)
                    .key(fileName)
                    .build();

            // S3와 스트림 연결
            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getObjectRequest);
            List<TelemetryDto> telemetries = new ArrayList<>();

            // 읽기
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3ObjectStream, StandardCharsets.UTF_8))) {
                List<String> lines = reader.lines().collect(Collectors.toList());
                for (String l : lines) {
                    System.out.println(l);
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        TelemetryDto telemetry = convertFromJson(line, TelemetryDto.class);
                        telemetries.add(telemetry);
                    }
                }
                log.info("S3로 부터 다운로드 성공: {}", fileName);
                return telemetries;
            }
        } catch (Exception e) {
            log.error("S3로 부터 다운로드 실패: {}", fileName, e);
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에서 텔레메트리 데이터를 다운로드하는 중 오류가 발생했습니다.");
        }
    }

    // 객체 -> JSON 문자열
    private String convertToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("객체 -> JSON 변환 실패: {}", object, e);
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "S3에 업로드를 위해 객체에서 JSON으로 변환하는 중 오류가 발생했습니다.");
        }
    }

    // JSON 문자열 -> 객체 변환 헬퍼 메서드
    private <T> T convertFromJson(String jsonString, Class<T> targetClass) {
        try {
            return objectMapper.readValue(jsonString, targetClass);
        } catch (Exception e) {
            log.error("JSON -> 객체 변환 실패: {}", jsonString, e);
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "S3에서 다운 받은 JSON을 객체로 변환하는 중 오류가 발생했습니다.");
        }
    }

}
