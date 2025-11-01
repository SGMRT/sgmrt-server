package soma.ghostrunner.global.clients.discord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Map;

@Component
public class DiscordWebhookClient {

    @Value("${discord.webhook.url}")
    private String DISCORD_WEBHOOK_URL;

    private final RestClient restClient = RestClient.create();

    public void sendMessage(String message) {
        Map<String, String> payload = Map.of("content", message);

        try {
            restClient.post()
                    .uri(URI.create(DISCORD_WEBHOOK_URL))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            System.out.println("웹훅 전송 성공");

        } catch (RestClientException e) {
            System.err.println("웹훅 전송 실패: " + e.getMessage());
        }
    }

}
