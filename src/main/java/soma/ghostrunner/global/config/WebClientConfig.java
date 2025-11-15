package soma.ghostrunner.global.config;

import io.micrometer.core.instrument.MeterRegistry;
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

    private static final int TIMEOUT_SECONDS = 300;

    @Bean
    public WebClient openAiWebClient(@Value("${openai.api.key}") String apiKey) {

        // 매 요청마다 새 TCP 커넥션을 생성하는 HttpClient
        HttpClient httpClient = HttpClient.create(ConnectionProvider.newConnection())
                // 1) TCP connect 타임아웃
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // 2) 응답 전체에 대한 타임아웃 (헤더/바디)
                .responseTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
