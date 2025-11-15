package soma.ghostrunner.domain.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.notification.application.PushIdempotencyService.LockResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static soma.ghostrunner.domain.notification.application.PushIdempotencyService.LockResult.*;

@DisplayName("PushIdempotencyService 통합 테스트")
class PushIdempotencyServiceTest extends IntegrationTestSupport {

    @Autowired private PushIdempotencyService idempotencyService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_MESSAGE_UUID = "test-message-uuid";
    private static final String TEST_PUSH_TOKEN = "ExponentPushToken[test-token-123]";

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.delete(redisTemplate.keys("push:idempotency:*"));
    }

    @Test
    @DisplayName("락 획득에 성공하면 레디스에 Key가 기록되며 LOCK_ACQUIRED를 반환한다.")
    void tryAcquireLock() {
        // when
        LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // then
        assertThat(result).isEqualTo(LOCK_ACQUIRED);

        // Redis 확인
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isEqualTo("PROCESSING");

        // TTL 확인 (30초)
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(20L).isLessThanOrEqualTo(30L);
    }

    @Test
    @DisplayName("이미 완료된 메시지에 대해 락 획득을 시도하면 ALREADY_COMPLETED를 반환한다.")
    void tryAcquireLock_alreadyCompleted() {
        // given - 먼저 완료 상태로 설정
        idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
        idempotencyService.markAsCompleted(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // when - 다시 락 획득 시도
        LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // then
        assertThat(result).isEqualTo(ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("다른 워커가 락을 보유 중일 때 락 획득을 시도하면 LOCKED_BY_OTHER를 반환한다.")
    void tryAcquireLock_lockedByOther() {
        // given - 먼저 락 획득
        LockResult firstResult = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
        assertThat(firstResult).isEqualTo(LOCK_ACQUIRED);

        // when - 다시 락 획득 시도
        LockResult secondResult = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // then
        assertThat(secondResult).isEqualTo(LOCKED_BY_OTHER);
    }

    @Test
    @DisplayName("메시지 전송 완료 처리 시 상태가 SENT로 업데이트되고 TTL이 길게 설정된다.")
    void markAsCompleted_assureSent() {
        // given
        idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // when
        idempotencyService.markAsCompleted(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // then
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isEqualTo("SENT");

        // TTL 확인 (6시간 = 21600초)
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(21000L).isLessThanOrEqualTo(21600L);
    }

    @Test
    @DisplayName("락 해제 시 키가 삭제되어 재시도 가능하다.")
    void releaseLock_deletesKey() {
        // given
        idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // when
        idempotencyService.releaseLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // then
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isNull();

        // 재시도 가능 확인
        LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
        assertThat(result).isEqualTo(LOCK_ACQUIRED);
    }

    @Test
    @DisplayName("TTL 만료 후 락 획득 시도 시 성공한다.")
    void tryAcquireLock_afterTTLExpired() throws InterruptedException {
        // given - PROCESSING 상태로 설정하되 TTL을 짧게 설정
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        redisTemplate.opsForValue().set(key, "PROCESSING", Duration.ofMillis(50));

        // when - 0.1초 대기 (TTL 만료)
        Thread.sleep(100);

        // then - 키가 자동 삭제되어 새로 획득 가능
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isNull();

        LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
        assertThat(result).isEqualTo(LOCK_ACQUIRED);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 락 획득 시도 시 정확히 하나만 성공한다.")
    void tryAcquireLock_concurrent() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<LockResult> results = new CopyOnWriteArrayList<>();

        // when - 동시에 락 획득 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작하도록
                    LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
                    results.add(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 시작
        doneLatch.await(10, TimeUnit.SECONDS); // 완료 대기
        executorService.shutdown();

        // then - 정확히 1개만 LOCK_ACQUIRED, 나머지는 LOCKED_BY_OTHER
        long acquiredCount = results.stream().filter(r -> r == LOCK_ACQUIRED).count();
        long lockedByOtherCount = results.stream().filter(r -> r == LOCKED_BY_OTHER).count();

        assertThat(acquiredCount).isEqualTo(1);
        assertThat(lockedByOtherCount).isEqualTo(threadCount - 1);
        assertThat(results).hasSize(threadCount);
    }

    @Test
    @DisplayName("동시 요청 환경에서 동일 UUID에 SETNX 실패 시 해당 요청은 ALREADY_COMPLETED임을 확인한다.")
    void tryAcquireLock_concurrent_SETNXfail() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger acquiredCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger lockedCount = new AtomicInteger(0);

        // Thread 1: 락 획득 -> 즉시 완료
        executorService.submit(() -> {
            try {
                startLatch.await();
                LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
                if (result == LOCK_ACQUIRED) {
                    acquiredCount.incrementAndGet();
                    Thread.sleep(10); // 짧은 처리 시간
                    idempotencyService.markAsCompleted(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2, 3: 약간의 지연 후 락 획득 시도
        for (int i = 0; i < 2; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    Thread.sleep(50); // Thread 1이 완료될 때까지 대기
                    LockResult result = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);
                    if (result == ALREADY_COMPLETED) {
                        completedCount.incrementAndGet();
                    } else if (result == LOCKED_BY_OTHER) {
                        lockedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // then - Thread 2, 3는 ALREADY_COMPLETED를 받아야 함
        assertThat(acquiredCount.get()).isEqualTo(1);
        assertThat(completedCount.get()).isEqualTo(2);
        assertThat(lockedCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 PushToken에 대해서 락이 독립적으로 설정된다.")
    void tryAcquireLock_DifferentTokens_IndependentLocks() {
        // given
        String token1 = "ExponentPushToken[token-1]";
        String token2 = "ExponentPushToken[token-2]";

        // when
        LockResult result1 = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, token1);
        LockResult result2 = idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, token2);

        // then - 각각 독립적으로 락 획득
        assertThat(result1).isEqualTo(LOCK_ACQUIRED);
        assertThat(result2).isEqualTo(LOCK_ACQUIRED);
    }

    @Test
    @DisplayName("여러 MessageUUID에 대해서 락이 독립적으로 설정된다.")
    void tryAcquireLock_DifferentMessageUuids_IndependentLocks() {
        // given
        String uuid1 = "message-uuid-1";
        String uuid2 = "message-uuid-2";

        // when
        LockResult result1 = idempotencyService.tryAcquireLock(uuid1, TEST_PUSH_TOKEN);
        LockResult result2 = idempotencyService.tryAcquireLock(uuid2, TEST_PUSH_TOKEN);

        // then - 각각 독립적으로 락 획득
        assertThat(result1).isEqualTo(LOCK_ACQUIRED);
        assertThat(result2).isEqualTo(LOCK_ACQUIRED);
    }
}

