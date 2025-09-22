package soma.ghostrunner.domain.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    @Value("${jwt.expiration_time.refresh_token}")
    private long REFRESH_TOKEN_TTL_SECONDS;

    public void saveToken(String memberUuid, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + memberUuid;
        redisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public Optional<String> findTokenByMemberUuid(String memberUuid) {
        String key = REFRESH_TOKEN_PREFIX + memberUuid;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void deleteTokenByMemberUuid(String memberUuid) {
        String key = REFRESH_TOKEN_PREFIX + memberUuid;
        redisTemplate.delete(key);
    }

}
