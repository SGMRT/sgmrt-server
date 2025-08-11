package soma.ghostrunner.clients.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import soma.ghostrunner.clients.aws.upload.S3TelemetryClient;


import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class S3TelemetryClientTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Utilities s3Utilities;

    private ObjectMapper objectMapper;
    private S3TelemetryClient sut;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sut = new S3TelemetryClient(objectMapper, s3Client);

        // @Value 주입 대체
        ReflectionTestUtils.setField(sut, "s3Bucket", "test-bucket");
        ReflectionTestUtils.setField(sut, "runningDirectory", "running");
        ReflectionTestUtils.setField(sut, "memberDirectory", "member");
        // 코스 디렉토리는 현재 @Value 애노테이션 오타가 있으니(괄호 미종결) 필요 시 동일하게 주입
        ReflectionTestUtils.setField(sut, "courseDirectory", "course");

        // S3 utilities mocking
        given(s3Client.utilities()).willReturn(s3Utilities);
        given(s3Utilities.getUrl(any(GetUrlRequest.class))).willAnswer(inv -> {
            GetUrlRequest r = inv.getArgument(0);
            return new URL("https://" + r.bucket() + ".s3.amazonaws.com/" + r.key());
        });
    }

    @Test
    @DisplayName("해상도를 줄인 코스 시계열 데이터를 S3에 업로드한다.")
    void uploadSimplifiedTelemetry() throws Exception {
        // given
        List<Map<String, Object>> telemetry = List.of(
                Map.of("timeStamp", 0, "lat", 37.1, "lng", 127.2),
                Map.of("timeStamp", 1000, "lat", 37.2, "lng", 127.3)
        );

        // putObject stubbing (리턴 타입 반영)
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build());

        // when
        String url = sut.uploadSimplifiedTelemetry(telemetry, "member-uuid-123");

        // then: 호출 검증 + 캡처
        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(reqCap.capture(), any(RequestBody.class));

        PutObjectRequest req = reqCap.getValue();
        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.contentType()).isEqualTo("application/jsonl");
        assertThat(req.key()).startsWith("course/member-uuid-123/").endsWith(".jsonl");
        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/" + req.key());
    }

    @Test
    @DisplayName("Raw Telemetry 데이터를 S3에 업로드한다.")
    @SneakyThrows
    void uploadRawTelemetry_success() {
        // given
        byte[] bytes = "line1\nline2\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "rawTelemetry", "telemetry.jsonl", "application/jsonl", bytes
        );

        // when
        String url = sut.uploadRawTelemetry(file, "mem-001");

        // then
        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(reqCap.capture(), any(RequestBody.class));
        PutObjectRequest req = reqCap.getValue();

        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.contentType()).isEqualTo("application/jsonl");
        assertThat(req.key()).startsWith("running/mem-001/").endsWith(".jsonl");
        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/" + req.key());
    }

    @Test
    @DisplayName("러닝 캡쳐 이미지를 S3에 업로드한다.")
    @SneakyThrows
    void uploadRunningCaptureImage_success() {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "img", "capture.png", "image/png", new byte[]{1, 2, 3}
        );


        // when
        String url = sut.uploadRunningCaptureImage(image, "runner-777");

        // then
        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(reqCap.capture(), any(RequestBody.class));
        PutObjectRequest req = reqCap.getValue();

        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.contentType()).isEqualTo("image/png");
        assertThat(req.key()).startsWith("running/runner-777/").endsWith(".png");
        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/" + req.key());
    }

    @Test
    @DisplayName("uploadMemberProfileImage: 프로필 이미지 업로드 성공 (확장자 기본 .jpg 처리)")
    @SneakyThrows
    void uploadMemberProfileImage_success() {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "img", "noext", "image/jpeg", new byte[]{9, 8, 7}
        );

        // when
        String url = sut.uploadMemberProfileImage(image, "member-abc");

        // then
        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(reqCap.capture(), any(RequestBody.class));
        PutObjectRequest req = reqCap.getValue();

        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.contentType()).isEqualTo("image/jpeg");
        assertThat(req.key()).startsWith("member/member-abc/").endsWith(".jpg");
        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/" + req.key());
    }

}
