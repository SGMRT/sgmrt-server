package soma.ghostrunner.domain.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushIdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "push:idempotency:";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SENT = "SENT";
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(30);
    private static final Duration SENT_TTL = Duration.ofHours(6);

    public enum LockResult {
        ALREADY_COMPLETED,      // 이미 처리 완료됨
        LOCKED_BY_OTHER,        // 다른 Worker가 락 보유 중
        LOCK_ACQUIRED           // 락 획득 성공
    }

    /** 멱등성 락 획득 시도 */
    public LockResult tryAcquireLock(String messageUuid, String pushToken) {
        try {
            // 전송 여부 확인
            String key = buildKey(messageUuid, pushToken);
            String status = (String) redisTemplate.opsForValue().get(key);
            if (STATUS_SENT.equals(status)) {
                return LockResult.ALREADY_COMPLETED;
            }

            // SETNX로 원자적 락 획득 시도
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, STATUS_PROCESSING, PROCESSING_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                // 락 획득하면 성공 반환
                return LockResult.LOCK_ACQUIRED;
            } else {
                // SETNX 실패 - SENT인지 PROCESSING인지 확인
                String currentStatus = (String) redisTemplate.opsForValue().get(key);
                if (STATUS_SENT.equals(currentStatus)) {
                    return LockResult.ALREADY_COMPLETED;
                } else {
                    log.info("다른 Worker가 락 보유 중: messageUuid={}, token={}", messageUuid, pushToken);
                    return LockResult.LOCKED_BY_OTHER;
                }
            }

        } catch (Exception e) {
            log.error("Redis에서 락 획득 실패하여 중복 체크 없이 진행: messageUuid={}, token={}", messageUuid, pushToken, e);
            return LockResult.LOCK_ACQUIRED; // Redis 다운 시 중복 체크 없이 전송 진행
        }
    }

    /** 푸시 전송 성공: 락을 SENT 상태로 업그레이드 */
    public void markAsCompleted(String messageUuid, String pushToken) {
        try {
            String key = buildKey(messageUuid, pushToken);
            redisTemplate.opsForValue().set(key, STATUS_SENT, SENT_TTL);
        } catch (Exception e) {
            log.error("Redis SENT 상태 저장 실패: messageUuid={}, token={}", messageUuid, pushToken, e);
        }
    }

    /** 푸시 전송 실패: 재시도 가능하도록 락 해제 */
    public void releaseLock(String messageUuid, String pushToken) {
        try {
            String key = buildKey(messageUuid, pushToken);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis 락 해제 실패: messageUuid={}, token={}", messageUuid, pushToken, e);
        }
    }

    private String buildKey(String messageUuid, String pushToken) {
        return KEY_PREFIX + messageUuid + ":" + pushToken;
    }

}
