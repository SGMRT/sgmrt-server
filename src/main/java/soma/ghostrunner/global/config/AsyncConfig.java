package soma.ghostrunner.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "llmTestExecutor")
    public Executor llmTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 기본 스레드 수
        executor.setMaxPoolSize(20);       // 최대 스레드 수
        executor.setQueueCapacity(5);     // 큐 용량
        executor.setThreadNamePrefix("llm-async-test-");
        executor.initialize();
        return executor;
    }

}
