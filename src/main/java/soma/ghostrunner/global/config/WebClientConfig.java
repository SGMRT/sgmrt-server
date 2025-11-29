package soma.ghostrunner.global.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 90;

    @Bean
    public WebClient openAiWebClient(@Value("${openai.api.key}") String apiKey) {

        // 매 연결마다 TCP Connection 생성 + 타임아웃 90s
        HttpClient httpClient = HttpClient.create(ConnectionProvider.newConnection())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS));

        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
