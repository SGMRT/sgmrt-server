package soma.ghostrunner.domain.running.infra.openai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import soma.ghostrunner.domain.running.domain.llm.PacemakerLlmClient;

@Slf4j
@Component
public class OpenAiClient implements PacemakerLlmClient {

    private final WebClient openAi;

    private final String OPENAI_MODEL = "gpt-5";

    public OpenAiClient(WebClient openAiWebClient) {
        this.openAi = openAiWebClient;
    }

    @Override
    public void requestLlmToCreatePacemaker(String prompt) {
        improveWorkout(prompt);
    }

    @Override
    public void improveWorkout(String prompt) {

        OpenAiRequest body = new OpenAiRequest(OPENAI_MODEL, prompt);
        log.info("LLM API 요청중..");

        openAi.post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .map(res -> {
                    if (res != null && res.output != null) {
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
                    return "";
                })
                .subscribe(
                        result -> {
                            // ✅ 성공 시 콜백
                            System.out.println("응답:\n" + result);
                        },
                        error -> {
                            // ❌ 실패 시 콜백
                            System.err.println("에러 발생: " + error.getMessage());
                            error.printStackTrace();
                        }
                );;
    }

    @Override
    public void fillVoiceGuidance(String prompt) {

    }

}
