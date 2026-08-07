package soma.ghostrunner.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import soma.ghostrunner.global.common.MdcTaskDecorator;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pushTaskExecutor")
    public Executor pushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("PushThread-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("llm-async-");

        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);

        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(1200);  // 20분 — 재시도 포함 워스트 (2회 호출 × 3시도 × 3분 + 백오프)까지 전부 대기
        executor.setAcceptTasksAfterContextClose(true);  // 셧다운 드레인 중인 HTTP 요청의 태스크 제출이 거부되지 않도록

        executor.initialize();
        return executor;
    }

}
