package soma.ghostrunner.domain.running.infra.openai;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PacemakerOpenAiClientTest {

    private MockWebServer mockWebServer;
    private PacemakerOpenAiClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/v1").toString();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer dummy")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        client = new PacemakerOpenAiClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("LLM API로 부터 성공의 응답이 온다.")
    void improveWorkout_success_noRetry() {
        // given: OpenAI 응답 모킹
        String responseBody = """
            {
              "output": [
                {
                  "content": [
                    { "type": "output_text", "text": "generated text" }
                  ]
                }
              ]
            }
            """;

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(responseBody)
                        .addHeader("Content-Type", "application/json")
        );

        // when & then
        StepVerifier.create(client.improveWorkout("PROMPT"))
                .expectNext("generated text")     // llmResponseToString 결과
                .verifyComplete();

        Assertions.assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("LLM API로 부터 1번의 실패가 오고 첫 번째 재시도에서 성공한다.")
    void improveWorkout_retryOn5xx_thenSuccess() {
        // given
        String okBody = """
        {
          "output": [
            {
              "content": [
                { "type": "output_text", "text": "ok after retry" }
              ]
            }
          ]
        }
        """;

        // 1번째 응답: 500
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody("server error")
        );
        // 2번째 응답: 200
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(okBody)
                        .addHeader("Content-Type", "application/json")
        );

        StepVerifier.create(client.improveWorkout("PROMPT"))
                .expectNext("ok after retry")
                .verifyComplete();

        Assertions.assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("모두 실패하면 재시도 횟수가 총 3번이다.")
    void improveWorkout_retryExhausted_thenError() {
        // 3번 모두 500 리턴
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.improveWorkout("PROMPT"))
                .expectErrorSatisfies(e -> {
                    // 1) 겉 예외는 RetryExhaustedException
                    Assertions.assertTrue(reactor.core.Exceptions.isRetryExhausted(e));

                    // 2) 안에 감긴 cause 가 WebClientResponseException(500)인지 확인
                    Throwable cause = e.getCause();
                    Assertions.assertTrue(cause instanceof WebClientResponseException);
                    WebClientResponseException ex = (WebClientResponseException) cause;
                    Assertions.assertEquals(500, ex.getRawStatusCode());
                })
                .verify();

        // 1 + 2회 재시도 = 총 3회 호출
        Assertions.assertEquals(3, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("4xx번 대의 실패라면 1번만 요청한다.")
    void improveWorkout_4xx_noRetry() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        StepVerifier.create(client.improveWorkout("PROMPT"))
                .expectError(WebClientResponseException.BadRequest.class)
                .verify();

        Assertions.assertEquals(1, mockWebServer.getRequestCount());
    }

}
