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
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;


import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GhostRunnerS3ClientTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Utilities s3Utilities;

    private ObjectMapper objectMapper;
    private GhostRunnerS3Client sut;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sut = new GhostRunnerS3Client(objectMapper, s3Client);

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
    @DisplayName("프론트엔드 단에서 보간한 러닝 시계열 데이터를 S3에 업로드한다.")
    void uploadInterpolatedTelemetry() {
        // given
        List<TelemetryDto> telemetryDtos = List.of(
                new TelemetryDto(0L, 37.1, 36.3, 5.0, 5.5, 110.0, 120, 130, true),
                new TelemetryDto(0L, 37.2, 36.4, 5.1, 5.6, 111.0, 121, 131, true),
                new TelemetryDto(0L, 37.3, 36.5, 5.2, 5.7, 112.0, 122, 132, true),
                new TelemetryDto(0L, 37.4, 36.6, 5.3, 5.8, 113.0, 123, 133, true),
                new TelemetryDto(0L, 37.5, 36.7, 5.4, 5.6, 114.0, 124, 134, true),
                new TelemetryDto(0L, 37.6, 36.8, 5.5, 6.0, 115.0, 125, 135, false)
        );

        // putObject stubbing (리턴 타입 반영)
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build());

        // when
        String url = sut.uploadInterpolatedTelemetry(telemetryDtos, "member-uuid-123");

        // then: 호출 검증 + 캡처
        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(reqCap.capture(), any(RequestBody.class));

        PutObjectRequest req = reqCap.getValue();
        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.contentType()).isEqualTo("application/jsonl");
        assertThat(req.key()).startsWith("running/member-uuid-123/").endsWith(".jsonl");
        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/" + req.key());
    }

    @Test
    @DisplayName("해상도를 줄인 코스 시계열 데이터를 S3에 업로드한다.")
    void uploadSimplifiedTelemetry() {
        // given
        List<CoordinateDto> coordinateDtos = List.of(
                new CoordinateDto(37.1, 36.3),
                new CoordinateDto(37.2, 36.4),
                new CoordinateDto(37.3, 36.5),
                new CoordinateDto(37.4, 36.6),
                new CoordinateDto(37.5, 36.7)
        );

        // putObject stubbing (리턴 타입 반영)
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build());

        // when
        String url = sut.uploadSimplifiedTelemetry(coordinateDtos, "member-uuid-123");

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
