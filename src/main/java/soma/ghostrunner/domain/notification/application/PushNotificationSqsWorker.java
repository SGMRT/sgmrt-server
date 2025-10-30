package soma.ghostrunner.domain.notification.application;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.notification.application.dto.NotificationRequest;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;
import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.notification.dao.PushTokenRepository;
import soma.ghostrunner.domain.notification.exception.ExpoDeviceNotRegisteredException;
import soma.ghostrunner.global.clients.discord.DiscordWebhookClient;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationSqsWorker {

    private final ExpoPushClient expoPushClient;
    private final DiscordWebhookClient discordWebhookClient;
    private final SqsWorkerInternalService internalService;

    @SqsListener(value = "${cloud.aws.sqs.push-queue-name}")
    public void handlePushMessage(final PushMessageDto pushMessageDto) throws IOException {
        if (pushMessageDto.pushToken() == null || pushMessageDto.pushToken().isEmpty()) {
            return;
        }
        try {
            List<NotificationSendResult> sendResult = expoPushClient.push(createNotificationRequest(pushMessageDto));
            validateSendResult(sendResult);
        } catch (ExpoDeviceNotRegisteredException ex) {
            log.warn("유효하지 않은 푸쉬토큰 삭제: {}", pushMessageDto.pushToken());
            deletePushToken(pushMessageDto.pushToken());
        } catch (Exception ex) {
            log.error("푸쉬 알림 전송 실패: {}", pushMessageDto, ex);
            throw ex;
        }
    }

    @SqsListener(value = "${cloud.aws.sqs.push-dlq-name}")
    public void handleFailedPushMessage(final PushMessageDto pushMessageDto) {
        log.error("전송에 실패한 푸쉬 알림 메시지: {}", pushMessageDto);
        discordWebhookClient.sendMessage(generateFailedPushNotificationMessage(pushMessageDto));
    }

    private String generateFailedPushNotificationMessage(PushMessageDto pushMessageDto) {
        return """
                # 푸쉬 알림 전송 실패!
                여러 차례 재전송했음에도 실패한 푸쉬 알림 메시지에요.
                ```
                %s
                ```
                """.formatted(pushMessageDto);
    }

    private NotificationRequest createNotificationRequest(PushMessageDto pushMessageDto) {
        return NotificationRequest.of(
                pushMessageDto.title(),
                pushMessageDto.body(),
                pushMessageDto.data(),
                List.of(pushMessageDto.pushToken())
        );
    }

    private void deletePushToken(String pushToken) {
        internalService.deletePushToken(pushToken);
    }

    private void validateSendResult(List<NotificationSendResult> results) {
        for (NotificationSendResult result : results) {
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

}

@Service
@RequiredArgsConstructor
class SqsWorkerInternalService {

    private final PushTokenRepository pushTokenRepository;

    @Transactional
    void deletePushToken(String token) {
        pushTokenRepository.deleteByToken(token);
    }

}
