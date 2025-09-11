package soma.ghostrunner.domain.running.infra.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisDistributedLockManager {

    private final RedissonClient redissonClient;

    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

}
