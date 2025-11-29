package soma.ghostrunner.domain.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.device.dao.DeviceRepository;
import soma.ghostrunner.domain.notification.application.dto.PushMessage;
import soma.ghostrunner.domain.notification.application.dto.PushSendResult;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.notification.exception.ExpoDeviceNotRegisteredException;
import soma.ghostrunner.global.clients.discord.DiscordWebhookClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PushSqsWorker 통합 테스트")
class PushSqsWorkerTest extends IntegrationTestSupport {

    @Autowired private PushSqsWorker pushSqsWorker;
    @Autowired private PushIdempotencyService idempotencyService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @MockitoBean private ExpoPushClient expoPushClient;
    @MockitoBean private DiscordWebhookClient discordWebhookClient;
    @MockitoBean private DeviceRepository deviceRepository;

    private static final String TEST_MESSAGE_UUID = "test-message-uuid-123";
    private static final String TEST_PUSH_TOKEN = "ExponentPushToken[test-token-abc]";
    private static final String TEST_TITLE = "테스트 제목";
    private static final String TEST_BODY = "테스트 내용";

    @BeforeEach
    void setUp() {
        // Redis 및 Mock 초기화
        redisTemplate.delete(redisTemplate.keys("push:idempotency:*"));
        reset(expoPushClient, discordWebhookClient, deviceRepository);
    }

    @DisplayName("메시지 수신 시 정상적으로 푸쉬 전송을 성공하고 멱등성 키를 SENT로 업데이트한다.")
    @Test
    void handlePushMessage() throws IOException {
        // given
        PushMessage pushMessage = createPushMessage();
        PushSendResult successResult = PushSendResult.ofSuccess(TEST_PUSH_TOKEN, "ticket-id");

        when(expoPushClient.push(any(PushMessage.class)))
                .thenReturn(List.of(successResult));

        // when
        pushSqsWorker.handlePushMessage(pushMessage);

        // then
        verify(expoPushClient, times(1)).push(pushMessage);
        verify(discordWebhookClient, never()).sendMessage(anyString());

        // Redis 확인 - SENT 상태
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isEqualTo("SENT");

        // TTL 확인 (6시간)
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isGreaterThan(21000L);
    }

    @DisplayName("수신한 메시지의 멱등성 키가 ALREADY_COMPLETED라면 디스코드로 알리고 푸시 전송을 스킵한다.")
    @Test
    void handlePushMessage_AlreadyCompleted() throws IOException {
        // given
        PushMessage pushMessage = createPushMessage();

        // 먼저 한 번 처리하여 SENT 상태로 만듦
        PushSendResult successResult = PushSendResult.ofSuccess(TEST_PUSH_TOKEN, "ticket-id");
        when(expoPushClient.push(any(PushMessage.class)))
                .thenReturn(List.of(successResult));
        pushSqsWorker.handlePushMessage(pushMessage);

        reset(expoPushClient, discordWebhookClient);

        // when - 같은 메시지 재처리 시도
        pushSqsWorker.handlePushMessage(pushMessage);

        // then - 푸시 전송 스킵
        verify(expoPushClient, never()).push(any());

        // 디스코드 알림 전송
        verify(discordWebhookClient, times(1)).sendMessage(contains("중복 푸시 알림 감지"));
    }

    @DisplayName("수신한 메시지의 멱등성 키가 LOCKED_BY_OTHER라면 푸시 전송을 스킵한다.")
    @Test
    void handlePushMessage_LockedByOther_SkipProcessing() throws IOException {
        // given
        PushMessage pushMessage = createPushMessage();

        // 락을 먼저 획득 (PROCESSING 상태)
        idempotencyService.tryAcquireLock(TEST_MESSAGE_UUID, TEST_PUSH_TOKEN);

        // when - 같은 메시지 처리 시도
        pushSqsWorker.handlePushMessage(pushMessage);

        // then - 푸시 전송 스킵
        verify(expoPushClient, never()).push(any());

        // 디스코드 알림은 없음 (LOCKED_BY_OTHER는 정상적인 동시성 제어)
        verify(discordWebhookClient, never()).sendMessage(anyString());
    }

    @DisplayName("수신한 메시지의 토큰이 유효하지 않은 경우 토큰을 삭제하고, 멱등성 키는 그대로 둔다.")
    @Test
    void handlePushMessage_InvalidToken_DeleteTokenAndNoLockRelease() throws IOException {
        // given
        PushMessage pushMessage = createPushMessage();
        when(expoPushClient.push(any(PushMessage.class)))
                .thenThrow(new ExpoDeviceNotRegisteredException());

        // when
        pushSqsWorker.handlePushMessage(pushMessage);

        // then
        verify(expoPushClient, times(1)).push(pushMessage);
        verify(deviceRepository, times(1)).deleteAllByTokenIn(List.of(TEST_PUSH_TOKEN));

        // Redis 확인
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isEqualTo("SENT");
    }

    @DisplayName("푸시 전송에 실패하면 멱등성 키가 해제되어 재시도할 수 있다.")
    @Test
    void handlePushMessage_PushFailed_ReleaseLockAndThrowException() throws IOException {
        // given
        PushMessage pushMessage = createPushMessage();

        when(expoPushClient.push(any(PushMessage.class)))
                .thenThrow(new RuntimeException("푸시 전송 실패"));

        // when & then
        assertThatThrownBy(() -> pushSqsWorker.handlePushMessage(pushMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("푸시 전송 실패");

        // Redis 확인 - 락 해제됨 (재시도 가능)
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isNull();

        // 재시도 가능 확인
        when(expoPushClient.push(any(PushMessage.class)))
                .thenReturn(List.of(PushSendResult.ofSuccess(TEST_PUSH_TOKEN, "ticket-id")));

        pushSqsWorker.handlePushMessage(pushMessage);
        verify(expoPushClient, times(2)).push(pushMessage);
    }

    @DisplayName("푸시 전송 시 에러 응답을 수신하면 예외가 발생하고 락을 해제한다.")
    @Test
    void handlePushMessage_ValidationFailed_ReleaseLockAndThrowException() throws IOException {
        // given
        PushMessage pushMessage = createPushMessage();
        PushSendResult errorResult = PushSendResult.ofFailure(TEST_PUSH_TOKEN, "Something went wrong");

        when(expoPushClient.push(any(PushMessage.class)))
                .thenReturn(List.of(errorResult));

        // when & then
        assertThatThrownBy(() -> pushSqsWorker.handlePushMessage(pushMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("푸시 알림 전송 실패");

        // Redis 확인 - 락 해제됨
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isNull();
    }

    @DisplayName("빈 토큰 리스트를 수신하면 처리를 스킵한다.")
    @Test
    void handlePushMessage_EmptyTokens_SkipProcessing() throws IOException {
        // given
        PushMessage pushMessage = new PushMessage(
                List.of(), // 빈 토큰 리스트
                TEST_TITLE,
                TEST_BODY,
                Map.of("key", "value"),
                TEST_MESSAGE_UUID
        );

        // when
        pushSqsWorker.handlePushMessage(pushMessage);

        // then
        verify(expoPushClient, never()).push(any());
        verify(discordWebhookClient, never()).sendMessage(anyString());
    }

    @DisplayName("null 토큰을 수신하면 처리를 스킵한다.")
    @Test
    void handlePushMessage_NullTokens_SkipProcessing() throws IOException {
        // given
        PushMessage pushMessage = new PushMessage(
                null, // null 토큰
                TEST_TITLE,
                TEST_BODY,
                Map.of("key", "value"),
                TEST_MESSAGE_UUID
        );

        // when
        pushSqsWorker.handlePushMessage(pushMessage);

        // then
        verify(expoPushClient, never()).push(any());
        verify(discordWebhookClient, never()).sendMessage(anyString());
    }

    @DisplayName("동시에 같은 메시지를 처리하려 해도 한 번만 푸시가 전송된다.")
    @Test
    void handlePushMessage_Concurrent() throws Exception {
        // given
        PushMessage pushMessage = createPushMessage();
        PushSendResult successResult = PushSendResult.ofSuccess(TEST_PUSH_TOKEN, "ticket-id");

        when(expoPushClient.push(any(PushMessage.class)))
                .thenReturn(List.of(successResult));

        // when - 동시에 같은 메시지 처리
        Thread thread1 = new Thread(() -> {
            try {
                pushSqsWorker.handlePushMessage(pushMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                pushSqsWorker.handlePushMessage(pushMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // then - 실제 푸시는 1번만 전송
        verify(expoPushClient, times(1)).push(pushMessage);

        // Redis 확인 - SENT 상태
        String key = "push:idempotency:" + TEST_MESSAGE_UUID + ":" + TEST_PUSH_TOKEN;
        String status = (String) redisTemplate.opsForValue().get(key);
        assertThat(status).isEqualTo("SENT");
    }

    @DisplayName("DLQ에서 메시지를 수신하면 디스코드로 전송한다.")
    @Test
    void handleFailedPushMessage_SendDiscordNotification() {
        // given
        PushMessage pushMessage = createPushMessage();

        // when
        pushSqsWorker.handleFailedPushMessage(pushMessage);

        // then
        verify(discordWebhookClient, times(1)).sendMessage(contains("푸쉬 알림 전송 실패"));
    }

    private PushMessage createPushMessage() {
        return new PushMessage(
                List.of(TEST_PUSH_TOKEN),
                TEST_TITLE,
                TEST_BODY,
                Map.of("data1", "value1", "data2", "value2"),
                TEST_MESSAGE_UUID
        );
    }
}

