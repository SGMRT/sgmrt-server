package soma.ghostrunner.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // 1. Netty의 이벤트 루프 리소스를 8개의 스레드를 가진 고정 스레드 풀을 생성
        LoopResources loopResources = LoopResources.create("my-netty-threads", 8, true);

        // 2. 위에서 만든 LoopResources를 사용하도록 HttpClient를 설정
        HttpClient httpClient = HttpClient.create()
                .runOn(loopResources);

        // 3. WebClient가 커스텀 HttpClient를 사용하도록 설정합니다.
        return builder
                .baseUrl("http://localhost:3000")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
