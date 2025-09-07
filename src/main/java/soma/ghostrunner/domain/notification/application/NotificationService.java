package soma.ghostrunner.domain.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.auth.event.MemberLoggedInEvent;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ExpoPushClient expoPushClient;
    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;

    @EventListener(NotificationEvent.class)
    public void handleNotificationEvent(NotificationEvent event) {
        // todo NotificationEventTranslator 구현 (외부 이벤트 -> NotificationEvent 변경)
        sendPushNotificationAsync(
                event.userIds(),
                event.title(),
                event.body(),
                event.data()
        );
    }

    public CompletableFuture<NotificationBatchResult> sendPushNotificationAsync(List<Long> userIds, String title, String body, Map<String, Object> data) {
        // - memberPushTokenRepository에서 List<String>인 푸쉬 토큰 리스트를 가져온다.
        List<PushToken> pushTokens = pushTokenRepository.findByMemberIdIn(userIds); // todo 메서드 구현

        // - Notification 엔티티를 먼저 생성한 후 저장한다.
        List<Notification> notifications = pushTokens.stream()
                .map(token -> Notification.of(token, title, body, data)) // 정적 팩토리 가정
                .toList();
        notificationRepository.saveAll(notifications);

        // - ExpoPushClient의 메소드를 호출한다.
        // - 호출이 끝날때까지 기다린 후, List<NotificationSendResult>에 기반하여 NotificationRepository의 status를 변경한다.
        // Q. 트랜잭션 분리: ExpoAClient 호출이 오래 걸릴 수도 있어서 이 메소드에 @Transactional을 뺌.
        // - 그럼 ExpoPushClient에 호출을 날린 건 성공했는데 갑자기 서버가 꺼지면 Notification은 SENT가 아닌 CREATED로 남지 않나?
        // - 해결방법은 메시지큐?
        NotificationRequest request = NotificationRequest.from(notifications);
        CompletableFuture<List<NotificationSendResult>> clientFuture = expoPushClient.pushAsync(notifications);
        return clientFuture.thenApplyAsync(results -> {
                    // - 결과 기반 DB 저장
                    // - todo 고도화: DB 접근 최적화 (성공한 애들 모아서 한 번에 SENT / 실패한 애들 모아서 한 번에 RETRYING)
                    for (NotificationSendResult result : results) {
                        notificationRepository.findById(result.notificationId())
                                .ifPresent(noti -> {
                                    if (result.isSuccess()) {
                                        noti.markAsSent(result.ticketId());
                                    } else {
                                        noti.markAsFailed();
                                    }
                            });
                    }

                    long success = results.stream().filter(NotificationSendResult::isSuccess).count();
                    int total = results.size();
                    int failure = total - (int) success;
                    return new NotificationBatchResult(total, (int) success, failure);

                })
                .exceptionally(ex -> {
                    // 푸쉬 실패 예외 처리 (네트워크 장애 등)
                    notifications.forEach(Notification::markAsFailed);
                    notificationRepository.saveAll(notifications);
                    return new NotificationBatchResult(notifications.size(), 0, notifications.size());
                });
    }

    @TransactionalEventListener(MemberLoggedInEvent.class)
    @Transactional
    public void saveMemberPushToken(MemberLoggedInEvent event) {
        // todo MemberLoggedInEvent publush 구현 (AuthService 회원가입 & 로그인).
        boolean exists = pushTokenRepository.existsByMemberIdAndToken(event.memberId(), event.pushToken());
        if (!exists) {
            Member member = memberRepository.findById(event.memberId()).orElseThrow(() -> new MemberNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            PushToken token = new PushToken(member, event.pushToken());
            pushTokenRepository.save(token);
        }
    }

}
