package soma.ghostrunner.domain.notification.application;

import io.sentry.spring.jakarta.tracing.SentrySpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;
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
        log.info("{}개의 푸시 알림 대기열 등록 완료 (푸시 알림: title={}, body={}, data={})", pushMessages.size(), title, body, data);
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
