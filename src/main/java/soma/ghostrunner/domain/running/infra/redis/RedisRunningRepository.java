package soma.ghostrunner.domain.running.infra.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisRunningRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;

    public void save(String key, String value, TimeUnit timeUnit, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    public Long incrementAndGet(String rateLimitKey, long dailyLimit, int expirationSeconds) {

        DefaultRedisScript<Long> rateLimiterScript = new DefaultRedisScript<>();
        rateLimiterScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate-limiter.lua")));
        rateLimiterScript.setResultType(Long.class);

        return redisTemplate.execute(
                rateLimiterScript,
                Collections.singletonList(rateLimitKey),
                String.valueOf(dailyLimit),
                String.valueOf(expirationSeconds)
        );
    }

}
