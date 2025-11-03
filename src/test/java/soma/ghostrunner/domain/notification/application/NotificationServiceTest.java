package soma.ghostrunner.domain.notification.application;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.dto.NotificationBatchResult;
import soma.ghostrunner.domain.notification.client.ExpoPushClient;
import soma.ghostrunner.domain.notification.dao.NotificationRepository;
import soma.ghostrunner.domain.notification.dao.DeviceRepository;
import soma.ghostrunner.domain.notification.domain.Device;



import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@DisplayName("NotificationService 통합 테스트")
class NotificationServiceTest extends IntegrationTestSupport {

    @Autowired
    private NotificationService notificationService;

    @MockitoBean
    private ExpoPushClient expoPushClient;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private Member member;
    private Device device;

    @BeforeEach
    void setUp() {
        member = Member.of("카리나", "profile-url");
        memberRepository.save(member);
        device = Device.of(member, "ExponentPushToken[xxxx]");
        deviceRepository.save(device);
    }

//    @AfterEach
//    void cleanUp() {
//        notificationRepository.deleteAllInBatch();
//        pushTokenRepository.deleteAllInBatch();
//        memberRepository.deleteAllInBatch();
//    }

//    @DisplayName("푸시 알림 전송이 성공하면 Notification이 SENT 상태로 저장된다.")
//    @Test
//    void sendPushNotifications_success() {
//        // given
//        List<Long> memberIds = List.of(member.getId());
//
//        AtomicReference<Long> notificationId = new AtomicReference<>(1L);
//        given(expoPushClient.pushAsync(any(NotificationRequest.class)))
//                .willAnswer(invocation -> {
//                    // 서비스가 전달한 NotificationRequest로부터 ID를 동적으로 가져옴
//                    NotificationRequest request = invocation.getArgument(0);
//                    notificationId.set(request.getIds().get(0).getNotificationId());
//                    List<NotificationSendResult> results = request.getIds().stream()
//                            .map(notiId -> NotificationSendResult.ofSuccess(notiId.getNotificationId(), "ticket-id-" + notiId.getNotificationId()))
//                            .collect(Collectors.toList());
//                    return CompletableFuture.completedFuture(results);
//                });
//
//        // when
//        NotificationBatchResult result = notificationService.sendPushNotification(memberIds, "알림 제목", "알림 본문", Collections.emptyMap())
//                .join();
//
//        // then
//        assertThat(result.totalCount()).isEqualTo(1);
//        assertThat(result.successCount()).isEqualTo(1);
//        assertThat(result.failureCount()).isEqualTo(0);
//
//        List<Notification> saved = notificationRepository.findAll();
//        Notification updatedNotification = notificationRepository.findById(result.successPushTokens().get(0)).orElseThrow();
//        assertThat(updatedNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
//        assertThat(updatedNotification.getTicketId()).isEqualTo("ticket-id-" + notificationId.get());
//    }

//    @DisplayName("푸시 알림 전송이 오류로 인해 실패하면 Notification의 상태를 FAILED로 변경한다.")
//    @Test
//    void sendPushNotifications_failed() {
//        // given
//        List<Long> memberIds = List.of(member.getId());
//
//        given(expoPushClient.pushAsync(any(NotificationRequest.class)))
//                .willAnswer(invocation -> {
//                    NotificationRequest request = invocation.getArgument(0);
//                    List<NotificationSendResult> results = request.getIds().stream()
//                            .map(notiId -> NotificationSendResult.ofFailure(notiId.getNotificationId(), "Invalid token"))
//                            .collect(Collectors.toList());
//                    return CompletableFuture.completedFuture(results);
//                });
//
//        // when
//        NotificationBatchResult result = notificationService.sendPushNotification(memberIds, "알림 제목", "알림 본문", Collections.emptyMap())
//                .join();
//
//        // then
//        assertThat(result.totalCount()).isEqualTo(1);
//        assertThat(result.successCount()).isEqualTo(0);
//        assertThat(result.failureCount()).isEqualTo(1);
//
//        Notification updatedNotification = notificationRepository.findById(result.failurePushTokens().get(0)).orElseThrow();
//        assertThat(updatedNotification.getStatus()).isEqualTo(NotificationStatus.FAILED);
//    }

//    @DisplayName("다수의 알림 전송이 성공하면 모든 Notification이 SENT 상태로 저장된다.")
//    @Test
//    void sendPushNotifications_bulk() {
//        // given
//        Member member2 = Member.of("윈터", "profile-url");
//        memberRepository.save(member2);
//        PushToken pushToken2 = new PushToken(member2, "expo-test-token-2");
//        pushTokenRepository.save(pushToken2);
//
//        List<Long> memberIds = List.of(member.getId(), member2.getId());
//
//        given(expoPushClient.pushAsync(any(NotificationRequest.class)))
//                .willAnswer(invocation -> {
//                    NotificationRequest request = invocation.getArgument(0);
//                    List<NotificationSendResult> results = request.getIds().stream()
//                            .map(notiId -> NotificationSendResult.ofSuccess(notiId.getNotificationId(), "ticket-id-" + notiId.getNotificationId()))
//                            .collect(Collectors.toList());
//                    return CompletableFuture.completedFuture(results);
//                });
//
//        // when
//        NotificationBatchResult result = notificationService.sendPushNotification(memberIds, "title", "body", Collections.emptyMap())
//                .join();
//
//        // then
//        assertThat(result.totalCount()).isEqualTo(2);
//        assertThat(result.successCount()).isEqualTo(2);
//        assertThat(result.failureCount()).isEqualTo(0);
//
//        List<Notification> updatedNotifications = notificationRepository.findAll();
//        List<Long> savedIds = updatedNotifications.stream().map(Notification::getId).toList();
//        assertThat(result.successPushTokens()).containsExactlyInAnyOrderElementsOf(savedIds);
//
//        assertThat(updatedNotifications).hasSize(2);
//        assertThat(updatedNotifications).extracting(Notification::getStatus)
//                .containsOnly(NotificationStatus.SENT);
//    }

    @DisplayName("새로운 푸쉬 토큰을 저장한다.")
    @Test
    void saveMemberPushToken_newToken() {
        // given
        String newPushToken = "ExponentPushToken[yyyy]";

        // when
        notificationService.saveMemberPushToken(member.getUuid(), newPushToken);

        // then
        boolean exists = deviceRepository.existsByMemberIdAndToken(member.getId(), newPushToken);
        assertThat(exists).isTrue();
    }

    @DisplayName("이미 존재하는 푸쉬 토큰은 중복 저장하지 않는다.")
    @Test
    void saveMemberPushToken_duplicateToken() {
        // given
        String existingPushToken = device.getToken();
        long initialCount = deviceRepository.count();

        // when
        notificationService.saveMemberPushToken(member.getUuid(), existingPushToken);

        // then
        long afterCount = deviceRepository.count();
        assertThat(afterCount).isEqualTo(initialCount);
    }

    @DisplayName("푸쉬 토큰 형식이 Expo Push Token이 아니면 예외를 발생시킨다.")
    @Test
    void saveMemberPushToken_invalidFormat() {
        // given
        String invalidPushToken = "i-am-invalid";

        // when & then
        assertThatThrownBy(() -> notificationService.saveMemberPushToken(member.getUuid(), invalidPushToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 Push Token 방식이 아닙니다");
    }

    @DisplayName("존재하지 않는 회원에 포쉬 토큰 저장 시 예외를 발생시킨다.")
    @Test
    void saveMemberPushToken_memberNotFound() {
        // given
        String nonExistentUuid = "not-found-member-uuid";
        String newPushToken = "ExponentPushToken[yyyy]";

        // when & then
        assertThatThrownBy(() -> notificationService.saveMemberPushToken(nonExistentUuid, newPushToken))
                .isInstanceOf(MemberNotFoundException.class);
    }

}