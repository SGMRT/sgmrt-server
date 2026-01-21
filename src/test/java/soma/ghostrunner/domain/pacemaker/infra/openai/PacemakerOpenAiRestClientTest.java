package soma.ghostrunner.domain.pacemaker.infra.openai;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PacemakerOpenAiRestClientTest {

    static MockWebServer mockWebServer;

    PacemakerOpenAiRestClient pacemakerOpenAiRestClient;

    @BeforeAll
    static void setupServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void shutdownServer() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setupClient() {
        RestClient restClient = RestClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        pacemakerOpenAiRestClient = new PacemakerOpenAiRestClient(restClient);
    }

    @Test
    void 성공응답이면_llm문자열을_잘추출해서_반환한다() {
        // given
        String responseJson = """
            {
              "output": [
                {
                  "content": [
                    { "type": "output_text", "text": "Hello" },
                    { "type": "noise", "text": "ignored" },
                    { "type": "output_text", "text": " World!" }
                  ]
                }
              ]
            }
        """;

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(responseJson)
                        .addHeader("Content-Type", "application/json")
        );

        // when
        String result = pacemakerOpenAiRestClient.improveWorkout("prompt");

        // then
        assertThat(result).isEqualTo("Hello World!");
    }

    @Test
    void 첫번째는_5xx_두번째는_성공하면_재시도후_정상반환한다() {
        // 1st: 500 → retry
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody("server error")
        );

        // 2nd: 200 → success
        String responseJson = """
            {
              "output": [
                { "content": [ { "type": "output_text", "text": "OK" } ] }
              ]
            }
        """;
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(responseJson)
                        .addHeader("Content-Type", "application/json")
        );

        String result = pacemakerOpenAiRestClient.fillVoiceGuidance("prompt");

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void 계속_5xx가나오면_MAX_RETRY후_예외를던진다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500)); // 3rd → throw

        assertThatThrownBy(() -> pacemakerOpenAiRestClient.improveWorkout("prompt"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 클라이언트오류_4xx는_재시도없이_바로_예외던진다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        assertThatThrownBy(() -> pacemakerOpenAiRestClient.improveWorkout("prompt"))
                .isInstanceOf(Exception.class); // RestClientResponseException
    }
}
