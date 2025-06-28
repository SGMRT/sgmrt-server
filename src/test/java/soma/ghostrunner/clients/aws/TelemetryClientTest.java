package soma.ghostrunner.clients.aws;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TelemetryClientTest {

    @Test
    void 파싱_테스트() {
        // when
        String s3Url = "https://my-bucket.s3.ap-northeast-2.amazonaws.com/telemetry/1/uuid.jsonl";
        int bucketEndIndex = s3Url.indexOf(".s3.");
        int pathStartIndex = s3Url.indexOf("/", bucketEndIndex + 4);

        // then
        Assertions.assertThat(s3Url.substring(pathStartIndex + 1)).isEqualTo("telemetry/1/uuid.jsonl");
    }

}
