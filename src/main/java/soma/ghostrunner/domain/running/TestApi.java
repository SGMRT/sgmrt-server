package soma.ghostrunner.domain.running;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import soma.ghostrunner.domain.running.infra.openai.OpenAiClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TestApi {

    private final RestTemplate restTemplate;
    private final String MOCK_API_URL = "http://0.0.0.0:3000/llm-test";
    private final WebClient webClient;

    @GetMapping("/test/sync")
    public String syncTest() {
        return callSync();
    }

    @GetMapping("/test/async")
    public String asyncTest() {
        callAsync(System.currentTimeMillis());
        return "비동기 요청 후 바로 리턴";
    }

    @GetMapping("/test/non-blocking")
    public String nonBlockingTest() {
        callMockApiNonBlocking(System.currentTimeMillis());
        return "논블로킹 요청 후 바로 리턴";
    }

    public String callSync() {
        log.info("동기 호출 시작: {}", Thread.currentThread().getName());
        String result = restTemplate.getForObject(MOCK_API_URL, String.class);
        log.info("동기 호출 완료: {}", Thread.currentThread().getName());
        return result;
    }

    @Async
    public CompletableFuture<String> callAsync(Long startTime) {
        log.info("비동기 호출 시작: {}", Thread.currentThread().getName());
        String response = restTemplate.getForObject(MOCK_API_URL, String.class);
        log.info("비동기 호출 완료: {}", Thread.currentThread().getName());
        log.info("비동기 응답시간 : {}", System.currentTimeMillis() - startTime);
        return CompletableFuture.completedFuture(response);
    }

    public void callMockApiNonBlocking(Long startTime) {
        webClient.get()
                .uri("/llm-test")
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        // onNext: 응답이 성공적으로 왔을 때 실행될 로직
                        response -> {
                            log.info("논블로킹 호출 완료: {}", Thread.currentThread().getName());
                            log.info("논블로킹 응답시간 : {}", System.currentTimeMillis() - startTime);
                        },
                        // onError: 오류가 발생했을 때 실행될 로직
                        error -> {
                            log.info("논블로킹 호출 실패: {}", Thread.currentThread().getName());
                            log.info("논블로킹 응답시간 : {}", System.currentTimeMillis() - startTime);
                        }
                );
    }

}
