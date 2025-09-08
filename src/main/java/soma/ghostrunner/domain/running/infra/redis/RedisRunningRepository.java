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

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    public Long incrementRateLimitCounter(String rateLimitKey, long dailyLimit, int expirationSeconds) {

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate-limiter.lua")));
        script.setResultType(Long.class);

        return redisTemplate.execute(
                script,
                Collections.singletonList(rateLimitKey),
                String.valueOf(dailyLimit),
                String.valueOf(expirationSeconds)
        );
    }

    public Long decrementRateLimitCounter(String rateLimitKey) {

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate-limiter-compensation.lua")));
        script.setResultType(Long.class);

        return redisTemplate.execute(
                script,
                Collections.singletonList(rateLimitKey)
        );
    }

}
