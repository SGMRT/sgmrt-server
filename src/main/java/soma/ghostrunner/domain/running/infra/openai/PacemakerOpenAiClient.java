package soma.ghostrunner.domain.running.infra.openai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import soma.ghostrunner.domain.running.domain.llm.PacemakerLlmClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class PacemakerOpenAiClient implements PacemakerLlmClient {

    private final WebClient openAiWebClient;

    private final String OPENAI_MODEL = "gpt-5";
//    private final String OPENAI_MODEL = "gpt-5-nano";

    @Override
    public Mono<String> improveWorkout(String prompt) {
        OpenAiRequest body = new OpenAiRequest(OPENAI_MODEL, prompt);
        return openAiWebClient.post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
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
                .map(this::llmResponseToString);
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
