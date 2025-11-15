package soma.ghostrunner.domain.running.infra.openai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import soma.ghostrunner.domain.running.domain.llm.PacemakerLlmClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PacemakerOpenAiClient implements PacemakerLlmClient {

    private final WebClient openAiWebClient;

    private final String OPENAI_MODEL = "gpt-5";

    @Override
    public Mono<String> improveWorkout(String prompt) {
        OpenAiRequest body = new OpenAiRequest(OPENAI_MODEL, prompt);
        return openAiWebClient.post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .retryWhen(
                        Retry.backoff(1, Duration.ofSeconds(2))   // 1회 재시도, 초기 wait = 2초
                                .filter(this::isRetryableException)   // 재시도 가능한 예외만
                )
                .map(this::llmResponseToString);
    }

    @Override
    public Mono<String> fillVoiceGuidance(String prompt) {
        OpenAiRequest body = new OpenAiRequest(OPENAI_MODEL, prompt);
        return openAiWebClient.post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .retryWhen(
                        Retry.backoff(1, Duration.ofSeconds(2))   // 1회 재시도, 초기 wait = 2초
                                .filter(this::isRetryableException)   // 재시도 가능한 예외만
                )
                .map(this::llmResponseToString);
    }

    private boolean isRetryableException(Throwable e) {

        if (e instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }

        return e instanceof IOException
                || e instanceof TimeoutException
                || e instanceof WebClientRequestException;
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
