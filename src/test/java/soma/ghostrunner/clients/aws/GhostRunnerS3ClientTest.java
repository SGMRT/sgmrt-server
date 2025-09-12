package soma.ghostrunner.clients.aws;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.global.error.exception.ExternalIOException;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GhostRunnerS3ClientTest {

    @Mock
    S3Client s3Client;
    @Mock
    S3Utilities s3Utilities;

    ObjectMapper objectMapper;
    GhostRunnerS3Client client;

    final String bucket = "ghostrunner-bucket";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper(); // 성공 케이스에서는 실제 사용
        client = new GhostRunnerS3Client(objectMapper, s3Client);
        // @Value 주입 필드 세팅
        ReflectionTestUtils.setField(client, "s3Bucket", bucket);
    }

    // 샘플 DTO
    static class Telemetry {
        public String id;
        public double pace;
        public Telemetry(String id, double pace) {
            this.id = id;
            this.pace = pace;
        }
    }

    @Test
    @DisplayName("uploadObjectList: 리스트를 JSONL로 직렬화하여 S3 putObject에 전달한다")
    void uploadObjectList_success() throws IOException {
        // given
        List<Telemetry> list = List.of(
                new Telemetry("t1", 4.35),
                new Telemetry("t2", 5.10)
        );
        String key = "runs/2025-09-12/sample.jsonl";
        URL expectedUrl = new URL("https://"+bucket+".s3.amazonaws.com/"+key);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-123").build());
        when(s3Utilities.getUrl(any(GetUrlRequest.class))).thenReturn(expectedUrl);
        when(s3Client.utilities()).thenReturn(s3Utilities);

        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCap = ArgumentCaptor.forClass(RequestBody.class);

        // when
        String url = client.uploadObjectList(list, key);

        // then (URL 반환)
        assertThat(url).isEqualTo(expectedUrl.toString());

        // putObject에 전달된 요청/바디 캡쳐
        verify(s3Client).putObject(reqCap.capture(), bodyCap.capture());
        PutObjectRequest putReq = reqCap.getValue();
        RequestBody body = bodyCap.getValue();

        // PutObjectRequest 메타 검증
        assertThat(putReq.bucket()).isEqualTo(bucket);
        assertThat(putReq.key()).isEqualTo(key);
        assertThat(putReq.contentType()).isEqualTo("application/jsonl");
        assertThat(putReq.contentLength()).isNotNull();

        // RequestBody 실제 바이트 확인 (JSONL)
        byte[] actual = body.contentStreamProvider().newStream().readAllBytes();
        String actualStr = new String(actual, StandardCharsets.UTF_8);

        // 기대 JSONL (각 줄이 한 객체 JSON)
        String[] lines = actualStr.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("\"id\":\"t1\"").contains("\"pace\":4.35");
        assertThat(lines[1]).contains("\"id\":\"t2\"").contains("\"pace\":5.1");

        // contentLength 일치
        assertThat(putReq.contentLength()).isEqualTo((Long) (long) actual.length);

        // getUrl 호출 검증
        ArgumentCaptor<GetUrlRequest> urlReqCap = ArgumentCaptor.forClass(GetUrlRequest.class);
        verify(s3Utilities).getUrl(urlReqCap.capture());
        assertThat(urlReqCap.getValue().bucket()).isEqualTo(bucket);
        assertThat(urlReqCap.getValue().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("uploadMultipartFile: MultipartFile을 그대로 바이트로 읽어 S3 putObject에 전달한다")
    void uploadMultipartFile_success() throws Exception {
        // given
        String key = "uploads/telemetry-raw.jsonl";
        byte[] src = String.join("\n",
                "{\"id\":\"a\",\"pace\":4.0}",
                "{\"id\":\"b\",\"pace\":4.2}"
        ).getBytes(StandardCharsets.UTF_8);

        MockMultipartFile mf = new MockMultipartFile(
                "file", "raw.jsonl", "application/jsonl", src
        );

        URL expectedUrl = new URL("https://"+bucket+".s3.amazonaws.com/"+key);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-abc").build());
        when(s3Utilities.getUrl(any(GetUrlRequest.class))).thenReturn(expectedUrl);
        when(s3Client.utilities()).thenReturn(s3Utilities);

        ArgumentCaptor<PutObjectRequest> reqCap = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCap = ArgumentCaptor.forClass(RequestBody.class);

        // when
        String url = client.uploadMultipartFile(mf, key);

        // then
        assertThat(url).isEqualTo(expectedUrl.toString());

        verify(s3Client).putObject(reqCap.capture(), bodyCap.capture());
        PutObjectRequest putReq = reqCap.getValue();
        RequestBody body = bodyCap.getValue();

        assertThat(putReq.bucket()).isEqualTo(bucket);
        assertThat(putReq.key()).isEqualTo(key);
        assertThat(putReq.contentType()).isEqualTo("application/jsonl");
        assertThat(putReq.contentLength()).isEqualTo(mf.getSize());

        byte[] uploaded = body.contentStreamProvider().newStream().readAllBytes();
        assertThat(uploaded).isEqualTo(src);

        // URL 유틸 호출 검증
        verify(s3Utilities).getUrl(any(GetUrlRequest.class));
    }

    @Test
    @DisplayName("uploadObjectList: 직렬화 중 예외 발생 시 ExternalIOException으로 래핑되어 던진다")
    void uploadObjectList_serializeError_wrapped() throws Exception {
        // ObjectMapper를 Mock으로 바꿔 실패 시나리오 구성
        ObjectMapper failing = mock(ObjectMapper.class);
        GhostRunnerS3Client failingClient = new GhostRunnerS3Client(failing, s3Client);
        ReflectionTestUtils.setField(failingClient, "s3Bucket", bucket);

        // 리스트 내 2개 중 첫 번째는 성공, 두 번째는 직렬화 예외
        when(failing.writeValueAsString(eq("ok"))).thenReturn("\"ok\"");
        when(failing.writeValueAsString(eq("bad"))).thenThrow(new JsonProcessingException("boom"){});

        // when & then
        assertThatThrownBy(() ->
                failingClient.uploadObjectList(List.of("ok", "bad"), "any.jsonl")
        )
                .isInstanceOf(ExternalIOException.class)
                .hasMessageContaining("S3에 리스트를 JSONL로 업로드하는데 실패했습니다.");

        // putObject는 호출되지 않아야 함
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

}
