package soma.ghostrunner.clients.aws;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import soma.ghostrunner.global.clients.aws.presign.S3PresignUrlClient;

@ExtendWith(MockitoExtension.class)
class S3PresignUrlClientTest {

    private final String s3Bucket = "test-bucket";
    private final String memberProfileDirectory = "test-profiles";

    @Mock
    S3Presigner s3Presigner;

    private S3PresignUrlClient s3PresignUrlClient;

    @BeforeEach
    void setUpS3PresignUrlClient() {
        s3PresignUrlClient = new S3PresignUrlClient(s3Bucket, memberProfileDirectory, s3Presigner);
    }

    @DisplayName("파일 확장자의 형식이 다르면 예외를 응답한다.")
    @Test
    void getMemberProfilePresignUrlWithInvalidExtensionTemplate() {
        // given
        String filename = "이복둥 사진";

        // when // then
        Assertions.assertThatThrownBy(() -> s3PresignUrlClient.getMemberProfilePresignUrl(filename))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로필 사진 확장자 형식이 잘못된 경우");
    }

    @DisplayName("파일 확장자가 jpg 혹은 jpeg가 아니라면 예외를 응답한다.")
    @Test
    void getMemberProfilePresignUrlWithInvalidExtension() {
        // given
        String filename = "이복둥 사진.hpec";

        // when // then
        Assertions.assertThatThrownBy(() -> s3PresignUrlClient.getMemberProfilePresignUrl(filename))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("허용되지 않은 프로필 사진 확장자");
    }

}
