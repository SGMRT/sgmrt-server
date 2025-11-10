package soma.ghostrunner.domain.notification.application;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.notification.application.dto.PushSendResult;
import soma.ghostrunner.domain.notification.application.dto.PushMessage;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.device.dao.DeviceRepository;
import soma.ghostrunner.domain.notification.exception.ExpoDeviceNotRegisteredException;
import soma.ghostrunner.global.clients.discord.DiscordWebhookClient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushSqsWorker {

    private final ExpoPushClient expoPushClient;
    private final DiscordWebhookClient discordWebhookClient;
    private final SqsWorkerInternalService internalService;

    private static final int MIN_BACKOFF_MILLIS = 125;
    private static final int MAX_BACKOFF_MILLIS = 1000;
    private final AtomicInteger backoffMillis = new AtomicInteger(MIN_BACKOFF_MILLIS);

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @SqsListener(value = "${cloud.aws.sqs.push-queue-name}")
    public void handlePushMessage(final PushMessage pushMessage) throws IOException {
        if (pushMessage.pushTokens() == null || pushMessage.pushTokens().isEmpty()) {
            log.warn("푸쉬 토큰이 없으므로 건너뜀: {}", pushMessage);
            return;
        }
        try {
            List<PushSendResult> sendResult = expoPushClient.push(pushMessage);
            validateSendResult(sendResult);
            backoffMillis.set(MIN_BACKOFF_MILLIS);
        } catch (ExpoDeviceNotRegisteredException ex) {
            log.warn("유효하지 않은 푸쉬 토큰 삭제: {}", pushMessage.pushTokens());
            deletePushToken(pushMessage.pushTokens());
        } catch (Exception ex) {
            log.error("푸쉬 알림 전송 실패: {}", pushMessage, ex);
            doExponentialBackoff();
            throw ex;
        }
    }

    @SqsListener(value = "${cloud.aws.sqs.push-dlq-name}")
    public void handleFailedPushMessage(final PushMessage pushMessage) {
        log.error("전송에 실패한 푸쉬 알림 메시지: {}", pushMessage);
        discordWebhookClient.sendMessage(generateFailedPushNotificationMessage(pushMessage));
    }

    private String generateFailedPushNotificationMessage(PushMessage pushMessage) {
        return """
                # 푸쉬 알림 전송 실패! (환경: %s)
                여러 차례 재전송했음에도 실패한 푸쉬 알림 메시지에요.
                ```
                %s
                ```
                """.formatted(activeProfile, pushMessage);
    }

    private void deletePushToken(List<String> pushTokens) {
        internalService.deletePushTokens(pushTokens);
    }

    private void validateSendResult(List<PushSendResult> results) {
        for (PushSendResult result : results) {
            if (result.isFailure()) {
                String errorMessage = result.errorMessage();
                if (errorMessage != null && errorMessage.contains("not a valid Expo push token")) {
                    throw new ExpoDeviceNotRegisteredException();
                } else {
                    throw new RuntimeException("푸시 알림 전송 실패: " + errorMessage);
                }
            }
        }
    }

    private void doExponentialBackoff() {
        try {
            int sleepMillis = backoffMillis.get();
            log.info("푸쉬 알림 재시도 전 대기: {}ms", sleepMillis);
            Thread.sleep(sleepMillis);
            int nextBackoff = Math.min(sleepMillis * 2, MAX_BACKOFF_MILLIS);
            backoffMillis.set(nextBackoff);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}

@Service
@RequiredArgsConstructor
class SqsWorkerInternalService {

    private final DeviceRepository deviceRepository;

    @Transactional
    void deletePushTokens(List<String> tokens) {
        deviceRepository.deleteAllByTokenIn(tokens);
    }

}
