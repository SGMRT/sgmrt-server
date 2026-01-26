package soma.ghostrunner.global.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM API용 CircuitBreaker 설정
 */
@Configuration
public class CircuitBreakerConfig {

    private static final String LLM_API_CIRCUIT_BREAKER = "llmApi";

    @Bean
    public CircuitBreaker llmApiCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        return circuitBreakerRegistry.circuitBreaker(LLM_API_CIRCUIT_BREAKER);
    }

}
