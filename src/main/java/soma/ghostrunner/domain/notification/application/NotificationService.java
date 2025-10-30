package soma.ghostrunner.domain.notification.application;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.dto.NotificationBatchResult;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;
import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.notification.dao.PushTokenRepository;
import soma.ghostrunner.domain.notification.domain.PushToken;
import soma.ghostrunner.domain.notification.domain.event.NotificationCommand;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final PushNotificationSqsSender sqsSender;
    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;

    public void sendPushNotification(NotificationCommand command) {
        sendPushNotification(
                command.memberIds(),
                command.title(),
                command.body(),
                command.data()
        );
    }

    @SentrySpan
    public void sendPushNotification(List<Long> userIds, String title, String body, Map<String, Object> data) {
        List<PushToken> pushTokens = pushTokenRepository.findByMemberIdIn(userIds);
        List<PushMessageDto> pushMessages = pushTokens.stream()
                .map(token -> new PushMessageDto(token.getToken(), title, body, data, null))
                .toList();
        sqsSender.sendMany(pushMessages);

//        if (pushTokens.isEmpty()) return CompletableFuture.completedFuture(NotificationBatchResult.ofEmpty());
//
//        // Expo Push API 비동기 호출
//        NotificationRequest request = NotificationRequest.of(title, body, data,
//                pushTokens.stream().map(PushToken::getToken).toList());
//        CompletableFuture<List<NotificationSendResult>> clientFuture = expoPushClient.pushAsync(request);
//        return clientFuture.thenApply(results -> {
//                    // 실패한 알림 처리
//                    handleNotificationResult(results);
//                    return createNotificationBatchResult(results);
//                })
//                .exceptionally(ex -> {
//                    // 푸쉬 실패 (네트워크 장애, 잘못된 요청 형식 등)
//                    log.error("푸시 알람 전송에 실패했습니다. %nException: {} %nRequest: {}", ex.getMessage(), request, ex);
//                    return new NotificationBatchResult(request.getTargetPushTokens().size(), 0, request.getTargetPushTokens().size(), List.of(), List.of());
//                });
    }

    private void handleNotificationResult(List<NotificationSendResult> results) {
        for (NotificationSendResult result : results) {
            if (result.isSuccess()) {
                log.info("NotificationService: Notification {} marked as SENT", result.pushToken());
            } else {
                // 재시도 필요
                log.warn("Notification failed to send: {}", result);
                // 존재하지 않는 토큰인 경우 DB에서 삭제
                if(result.errorMessage().contains("is not a valid Expo push token")) {
                    log.info("{} << 존재하지 않으니까 DB에서 삭제해야 됨", result);
                    continue;
                }
            }
        }
    }

    private static NotificationBatchResult createNotificationBatchResult(List<NotificationSendResult> results) {
        long success = results.stream().filter(NotificationSendResult::isSuccess).count();
        int total = results.size();
        int failure = total - (int) success;
        List<String> successfulTokens = results.stream().filter(NotificationSendResult::isSuccess).map(NotificationSendResult::pushToken).toList();
        List<String> failedTokens = results.stream().filter(NotificationSendResult::isFailure).map(NotificationSendResult::pushToken).toList();
        return new NotificationBatchResult(total, (int) success, failure, successfulTokens, failedTokens);
    }

    @Transactional
    public void saveMemberPushToken(String memberUuid, String pushToken) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        validatePushTokenFormat(pushToken);
        boolean exists = pushTokenRepository.existsByMemberIdAndToken(member.getId(), pushToken);
        if (!exists) {
            log.info("NotificationService: Saving push token {} for member uuid {}", pushToken, memberUuid);
            PushToken token = new PushToken(member, pushToken);
            pushTokenRepository.save(token);
        }
    }

    private void validatePushTokenFormat(String pushToken) {
        if (pushToken == null || !pushToken.startsWith("ExponentPushToken[")) {
            throw new IllegalArgumentException("올바른 Push Token 방식이 아닙니다: " + pushToken);
        }
    }

}
