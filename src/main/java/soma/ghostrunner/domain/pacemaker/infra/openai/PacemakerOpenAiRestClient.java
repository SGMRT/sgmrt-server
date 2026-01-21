package soma.ghostrunner.domain.pacemaker.infra.openai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import soma.ghostrunner.domain.pacemaker.domain.llm.PacemakerLlmClient;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PacemakerOpenAiRestClient implements PacemakerLlmClient {

    private final RestClient openAiRestClient;

    private static final String OPENAI_MODEL = "gpt-5";
    private static final int MAX_RETRY = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);

    @Override
    public String improveWorkout(String prompt) {
        return callOpenAi(prompt);
    }

    @Override
    public String fillVoiceGuidance(String prompt) {
        return callOpenAi(prompt);
    }

    private String callOpenAi(String prompt) {

        OpenAiRequest body = new OpenAiRequest(OPENAI_MODEL, prompt);
        int attempts = 0 ;

        while ( true ) {

            try {

                OpenAiResponse response = openAiRestClient.post()
                        .uri("/responses")
                        .body(body)
                        .retrieve()
                        .body(OpenAiResponse.class);

                return llmResponseToString(response);

            } catch (RestClientResponseException ex) {      // 실패 응답

                if ( !isRetryableException(ex) || attempts >= MAX_RETRY ) {
                    log.warn("OpenAI API 요청에서 실패함. status = {}, body = {}",
                            ex.getRawStatusCode(), ex.getResponseBodyAsString(), ex);
                    throw ex;
                }

            } catch (ResourceAccessException ex) {       // I/O 예외

                if (!isRetryableException(ex) || attempts >= MAX_RETRY) {
                    log.warn("OpenAI API 요청중 I/O 과정에서 실패함. attempts={}", attempts + 1, ex);
                    throw ex;
                }

            } catch (RestClientException ex) {          // 기타 에러

                if (!isRetryableException(ex) || attempts >= MAX_RETRY) {
                    log.warn("OpenAI API 요청에서 실패함.", ex);
                    throw ex;
                }

            }

            // 재시도 with 2초 백오프
            log.info("OpenAI API로 요청 재시도중.. attempts = {}", attempts + 1);

            attempts++;
            try {
                Thread.sleep(RETRY_BACKOFF.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("인터럽트로 인해서 백오프 중단됨.", ie);
            }

        }

    }

    private boolean isRetryableException(Throwable e) {

        // HTTP 5xx → 재시도
        if (e instanceof RestClientResponseException ex) {
            HttpStatusCode statusCode = ex.getStatusCode();
            return statusCode.is5xxServerError();
        }

        // I/O, 타임아웃 등 → 재시도
        if (e instanceof ResourceAccessException) {
            return true;
        }

        // 그 외 RestClientException은 기본적으로 재시도 X
        return false;

    }

    private String llmResponseToString(OpenAiResponse res) {

        StringBuilder sb = new StringBuilder();

        for (OpenAiResponse.OutputItem item : res.output) {
            if (item.content != null) {
                for (OpenAiResponse.Content c : item.content) {
                    if ("output_text".equalsIgnoreCase(c.type) && c.text != null) {
                        sb.append(c.text);
                    }
                }
            }
        }

        return sb.toString();
    }

}
