package soma.ghostrunner.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiRestClientConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 180;  // 3분 (LLM 응답 대기)

    @Bean
    public RestClient openAiRestClient(@Value("${openai.api.key}") String apiKey) {

        // 타임아웃 설정
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(RESPONSE_TIMEOUT_SECONDS * 1000);

        return RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
