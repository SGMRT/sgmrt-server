package soma.ghostrunner.global.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient openAiWebClient(@Value("${openai.api.key}") String apiKey) {
        final int TIMEOUT_SECONDS = 180;

        // Connection Provider 설정
        ConnectionProvider provider = ConnectionProvider.builder("openai-pool")
                .maxConnections(50) // 최대 커넥션 수
                .maxIdleTime(Duration.ofSeconds(45)) // 유휴 커넥션 유지 시간 (서버 타임아웃보다 짧게 설정)
                .maxLifeTime(Duration.ofMinutes(5))  // 커넥션 최대 수명
                .pendingAcquireTimeout(Duration.ofSeconds(60)) // 풀에서 커넥션을 얻기까지 대기 시간
                .evictInBackground(Duration.ofSeconds(120)) // 백그라운드에서 만료된 커넥션 정리 주기
                .build();

        HttpClient httpClient = HttpClient.create(provider) // 커스텀 provider 사용
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
