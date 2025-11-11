package soma.ghostrunner.global.clients.discord;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
public class DiscordWebhookClient {

    @Value("${discord.webhook.url}")
    private String DISCORD_WEBHOOK_URL;

    private final RestClient restClient = RestClient.create();

    public void sendMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        Map<String, String> payload = Map.of("content", message);

        try {
            restClient.post()
                    .uri(URI.create(DISCORD_WEBHOOK_URL))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("웹훅 전송 성공, 메시지: '{}'", message.replace("\n", " ").replace("`", ""));

        } catch (RestClientException e) {
            log.error("웹훅 전송 실패: ", e);
        }
    }

}
