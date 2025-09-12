package soma.ghostrunner.global.clients.aws.s3;

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
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.ExternalIOException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GhostRunnerS3Client {

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String s3Bucket;

    public String uploadObjectList(List<?> objectList, String fileName) {
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

    public String uploadMultipartFile(MultipartFile multipartFile, String fileName) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(fileName)
                    .contentLength(multipartFile.getSize())
                    .build();

            log.info("S3에 MultipartFile JSONL 업로드 중.. 파일 이름: {}, 크기: {} bytes", fileName, multipartFile.getSize());
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize()));

            log.info("S3에 MultipartFile JSONL 업로드 성공. 파일: {}", fileName);
            return getS3FileUrl(fileName);
        } catch (Exception e) {
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에 MultipartFile(JSONL)을 업로드하는데 실패했습니다.");
        }
    }

    private String getS3FileUrl(String fileKey) {
        return s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(s3Bucket).key(fileKey).build()).toString();
    }

}
