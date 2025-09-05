package soma.ghostrunner.domain.running.infra.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class RedisRateLimiterRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimiterScript;

    public RedisRateLimiterRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimiterScript = new DefaultRedisScript<>();
        this.rateLimiterScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate-limiter.lua")));
        this.rateLimiterScript.setResultType(Long.class);
    }

    public Long incrementAndGet(String rateLimitKey, long dailyLimit, int expirationSeconds) {
        return redisTemplate.execute(
                rateLimiterScript,
                Collections.singletonList(rateLimitKey),
                String.valueOf(dailyLimit),
                String.valueOf(expirationSeconds)
        );
    }

}
