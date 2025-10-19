package soma.ghostrunner.domain.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.dto.NotificationBatchResult;
import soma.ghostrunner.domain.notification.application.dto.NotificationRequest;
import soma.ghostrunner.domain.notification.application.dto.NotificationSendResult;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.notification.dao.PushTokenRepository;
import soma.ghostrunner.domain.notification.dao.NotificationRepository;
import soma.ghostrunner.domain.notification.domain.Notification;
import soma.ghostrunner.domain.notification.domain.PushToken;
import soma.ghostrunner.domain.notification.domain.event.NotificationEvent;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ExpoPushClient expoPushClient;
    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;

    @TransactionalEventListener
    public void handleNotificationEvent(NotificationEvent event) {
        // todo NotificationEventTranslator 구현 (외부 이벤트 listen -> NotificationEvent publish)
        sendPushNotificationAsync(
                event.userIds(),
                event.title(),
                event.body(),
                event.data()
        );
    }

    public CompletableFuture<NotificationBatchResult> sendPushNotificationAsync(List<Long> userIds, String title, String body, Map<String, Object> data) {
        List<PushToken> pushTokens = pushTokenRepository.findByMemberIdIn(userIds);
        if (pushTokens.isEmpty()) return CompletableFuture.completedFuture(NotificationBatchResult.ofEmpty());

        // 알림 전송 전에 Notification 엔티티 먼저 생성 및 저장
        List<Notification> notifications = pushTokens.stream()
                .map(token -> Notification.of(token, title, body, data))
                .toList();
        notificationRepository.saveAll(notifications);

        // Expo Push API 비동기 호출
        NotificationRequest request = NotificationRequest.from(notifications);
        CompletableFuture<List<NotificationSendResult>> clientFuture = expoPushClient.pushAsync(request);
        return clientFuture.thenApply(results -> {
                    // 전송 결과 DB에 반영 (Notification status 변경)
                    updateNotificationStatus(results, notifications);
                    return createNotificationBatchResult(results);
                })
                .exceptionally(ex -> {
                    // 푸쉬 실패 (네트워크 장애 등)
                    log.warn("NotificationService: exception while sending push notification: {}", ex.getMessage());
                    notifications.forEach(Notification::markAsFailed);
                    notificationRepository.saveAll(notifications);
                    List<Long> failureIds = notifications.stream().map(Notification::getId).toList();
                    return new NotificationBatchResult(notifications.size(), 0, notifications.size(), List.of(), failureIds);
                });
    }

    private void updateNotificationStatus(List<NotificationSendResult> results, List<Notification> notifications) {
        // todo 고도화: DB 접근 최적화 (성공한 애들 모아서 한 번에 SENT / 실패한 애들 모아서 한 번에 RETRYING)
        // Map<Id, Notification>으로 메모리에 있는 엔티티 컬렉션 재활용
        Map<Long, Notification> notificationMap = notifications.stream()
                .collect(Collectors.toMap(Notification::getId, Function.identity()));
        List<Long> successIds = new ArrayList<>(), failureIds = new ArrayList<>();

        for (NotificationSendResult result : results) {
            Notification noti = notificationMap.get(result.notificationId());
            if (noti == null) continue;

            if (result.isSuccess()) {
                noti.markAsSent(result.ticketId());
                successIds.add(result.notificationId());
                log.info("NotificationService: Notification {} marked as SENT", noti.getId());
            } else {
                // 존재하지 않는 토큰인 경우 DB에서 삭제
                if(result.errorMessage().contains("is not a valid Expo push token")) {
                    log.info("- 이거는 존재하지 않으니까 DB에서 삭제해야 됨");
                    continue;
                }
                failureIds.add(result.notificationId());
                noti.markAsFailed();  // TODO FAILED 대신 RETRYING 으로 변경 후 재시도 로직 구현
            }
        }

        notificationRepository.saveAll(notifications); // 상태 일괄 변경
    }

    private static NotificationBatchResult createNotificationBatchResult(List<NotificationSendResult> results) {
        long success = results.stream().filter(NotificationSendResult::isSuccess).count();
        int total = results.size();
        int failure = total - (int) success;
        List<Long> successIds = results.stream().filter(NotificationSendResult::isSuccess).map(NotificationSendResult::notificationId).toList();
        List<Long> failureIds = results.stream().filter(NotificationSendResult::isFailure).map(NotificationSendResult::notificationId).toList();
        return new NotificationBatchResult(total, (int) success, failure, successIds, failureIds);
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
