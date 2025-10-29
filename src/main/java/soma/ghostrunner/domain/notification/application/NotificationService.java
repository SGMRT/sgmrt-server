package soma.ghostrunner.domain.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.dto.NotificationBatchResult;
import soma.ghostrunner.domain.notification.application.dto.NotificationRequest;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.notification.dao.PushTokenRepository;
import soma.ghostrunner.domain.notification.domain.PushToken;
import soma.ghostrunner.domain.notification.domain.event.NotificationCommand;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ExpoPushClient expoPushClient;
    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;

    public void sendPushNotification(NotificationCommand command) {
        sendPushNotificationAsync(
                command.userIds(),
                command.title(),
                command.body(),
                command.data()
        );
    }

    public CompletableFuture<NotificationBatchResult> sendPushNotificationAsync(List<Long> userIds, String title, String body, Map<String, Object> data) {
        List<PushToken> pushTokens = pushTokenRepository.findByMemberIdIn(userIds);
        if (pushTokens.isEmpty()) return CompletableFuture.completedFuture(NotificationBatchResult.ofEmpty());

        // Expo Push API 비동기 호출
        NotificationRequest request = NotificationRequest.of(title, body, data,
                pushTokens.stream().map(PushToken::getToken).toList());
        CompletableFuture<List<NotificationSendResult>> clientFuture = expoPushClient.pushAsync(request);
        return clientFuture.thenApply(results -> {
                    // 실패한 알림 처리
                    handleNotificationResult(results);
                    return createNotificationBatchResult(results);
                })
                .exceptionally(ex -> {
                    // 푸쉬 실패 (네트워크 장애, 잘못된 요청 형식 등)
                    StringBuilder sb = new StringBuilder();
                    sb.append("exception while sending push notification: {}")
                            .append(ex.getMessage()).append("\n");
                    Arrays.stream(ex.getStackTrace()).limit(30)
                            .forEach(st -> sb.append("  at ").append(st.toString()).append("\n"));
                    log.error(sb.toString());
                    return new NotificationBatchResult(request.getTargetPushTokens().size(), 0, request.getTargetPushTokens().size(), List.of(), List.of());
                });
    }

    private void handleNotificationResult(List<NotificationSendResult> results) {
        // todo 고도화: DB 접근 최적화 (성공한 애들 모아서 한 번에 SENT / 실패한 애들 모아서 한 번에 RETRYING)
        // Map<Id, Notification>으로 메모리에 있는 엔티티 컬렉션 재활용
//        Map<Long, Notification> notificationMap = notifications.stream()
//                .collect(Collectors.toMap(Notification::getId, Function.identity()));
//        List<Long> successIds = new ArrayList<>(), failureIds = new ArrayList<>();

        for (NotificationSendResult result : results) {
//            Notification noti = notificationMap.get(result.notificationId());
//            if (noti == null) continue;

            if (result.isSuccess()) {
//                noti.markAsSent(result.ticketId());
//                successIds.add(result.notificationId());
                log.info("NotificationService: Notification {} marked as SENT", result.pushToken());
            } else {
                // 재시도 필요
                log.warn("Notification failed to send: {}", result);
                // 존재하지 않는 토큰인 경우 DB에서 삭제
                if(result.errorMessage().contains("is not a valid Expo push token")) {
                    log.info("{} << 존재하지 않으니까 DB에서 삭제해야 됨", result);
                    continue;
                }

//                failureIds.add(result.notificationId());
//                noti.markAsFailed();  // TODO FAILED 대신 RETRYING 으로 변경 후 재시도 로직 구현
            }
        }

//        notificationRepository.saveAll(notifications); // 상태 일괄 변경
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
