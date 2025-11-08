package soma.ghostrunner.domain.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.dto.PushContent;
import soma.ghostrunner.domain.device.dao.DeviceRepository;
import soma.ghostrunner.domain.device.domain.Device;
import soma.ghostrunner.domain.notification.application.dto.PushMessage;
import soma.ghostrunner.domain.notification.dao.PushHistoryRepository;
import soma.ghostrunner.domain.notification.domain.PushHistory;
import soma.ghostrunner.domain.notification.exception.PushHistoryNotFound;
import soma.ghostrunner.global.common.versioning.SemanticVersion;
import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@DisplayName("NotificationService 통합 테스트")
class PushServiceTest extends IntegrationTestSupport {

    @Autowired
    private PushService pushService;

    @MockitoBean
    private PushSqsSender sqsSender; // SQS 연동은 모킹

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PushHistoryRepository pushHistoryRepository;

    @Autowired
    private MemberRepository memberRepository;


    private Member member1, member2;

    @BeforeEach
    void setUp() {
        member1 = Member.of("카리나", "profile-url");
        member2 = Member.of("윈터", "profile-url");
        memberRepository.saveAll(List.of(member1, member2));
    }

    /* push() 테스트 */

    @DisplayName("특정 회원에게 푸시알림을 전송한다. 회원의 Device를 찾아 푸시를 전송한다.")
    @Test
    void push() {
        // given
        // 2개의 유효한 디바이스 (버전 일치)
        createAndSaveDevice(member1, "ExponentPushToken[1]", "1.1.0");
        PushContent content = createPushContent();

        // when
        int pushCount = pushService.push(member1.getId(), content);

        // then
        assertThat(pushCount).isEqualTo(1);

        // PushHistory 검증
        List<PushHistory> histories = pushHistoryRepository.findAll();
        assertThat(histories).hasSize(1);
        PushHistory savedHistory = histories.get(0);
        assertThat(savedHistory.getMemberId()).isEqualTo(member1.getId());
        assertThat(savedHistory.getTitle()).isEqualTo(content.title());
        assertThat(savedHistory.getBody()).isEqualTo(content.body());

        // SQS Sender 호출 검증 (인자에 토큰 1개)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        then(sqsSender).should(times(1)).sendMany(captor.capture());

        List<PushMessage> capturedMessages = captor.getValue();
        assertThat(capturedMessages).hasSize(1);
        assertThat(capturedMessages.get(0).pushTokens()).contains("ExponentPushToken[1]");

        // SQS로 전송된 data에 history ID 포함 검증
        assertThat(capturedMessages.get(0).data())
                .containsEntry("id", savedHistory.getId());
    }

    @DisplayName("수신자에게 푸시 허용 기기가 없는 경우 PushHistory 저장 없이 종료한다.")
    @Test
    void push_pushableDeviceEmpty() {
        // given
        createAndSaveDevice(member1, null, "1.1.0");
        createAndSaveDevice(member1, "ExponentPushToken[1]", "1.0.2");
        PushContent content = createPushContent(VersionRange.atLeast("1.1.0"));

        // when
        int pushCount = pushService.push(member1.getId(), content);

        // then
        assertThat(pushCount).isEqualTo(0);
        assertThat(pushHistoryRepository.findAll()).hasSize(0);
        // SQS 호출 X
        verify(sqsSender, never()).sendMany(any());
    }

    @DisplayName("회원에게 푸시 전송 가능한 기기가 여러 개 있어도 PushHistory는 하나만 저장된다.")
    @Test
    void push_multiplePushableDevices() {
        // given
        // 2개의 유효한 디바이스 (버전 일치)
        createAndSaveDevice(member1, "ExponentPushToken[1]", "1.1.0");
        createAndSaveDevice(member1, "ExponentPushToken[2]", "1.1.0");
        PushContent content = createPushContent();

        // when
        int pushCount = pushService.push(member1.getId(), content);

        // then
        assertThat(pushCount).isEqualTo(2);

        // PushHistory 1건 저장되었는지 검증
        List<PushHistory> histories = pushHistoryRepository.findAll();
        assertThat(histories).hasSize(1);
        PushHistory savedHistory = histories.get(0);
        assertThat(savedHistory.getMemberId()).isEqualTo(member1.getId());
        assertThat(savedHistory.getTitle()).isEqualTo(content.title());
        assertThat(savedHistory.getBody()).isEqualTo(content.body());

        // SQS Sender 호출 검증 (인자에 토큰 2개)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        then(sqsSender).should(times(1)).sendMany(captor.capture());

        List<PushMessage> capturedMessages = captor.getValue();
        assertThat(capturedMessages).hasSize(2);
        assertThat(capturedMessages.get(0).pushTokens()).contains("ExponentPushToken[1]");
        assertThat(capturedMessages.get(1).pushTokens()).contains("ExponentPushToken[2]");

        // SQS로 전송된 data에 history ID 포함 검증
        assertThat(capturedMessages.get(0).data())
                .containsEntry("id", savedHistory.getId());
    }

    /* broadcast() 테스트 */

    @DisplayName("전체 푸시: 버전/토큰이 유효한 모든 멤버의 디바이스에 전송한다")
    @Test
    void broadcast_success() {
        // given
        // Member 1 (유효 디바이스 2개)
        createAndSaveDevice(member1, "ExponentPushToken[1]", "1.1.0");
        createAndSaveDevice(member1, "ExponentPushToken[2]", "1.2.0");
        // Member 2 (유효 디바이스 1개, 유효하지 않은 디바이스 2개)
        createAndSaveDevice(member2, "ExponentPushToken[3]", "1.1.0");
        createAndSaveDevice(member2, "ExponentPushToken[4]", "1.0.0"); // 버전 미스매치
        createAndSaveDevice(member2, null, "1.1.0"); // 토큰 없음

        PushContent content = createPushContent(VersionRange.atLeast("1.1.0"));

        // when
        int pushCount = pushService.broadcast(content);

        // then
        assertThat(pushCount).isEqualTo(3);

        // broadcast는 히스토리를 저장하지 않음
        assertThat(pushHistoryRepository.count()).isEqualTo(0);

        // SQS Sender가 3개의 메시지로 호출됨
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        then(sqsSender).should(times(1)).sendMany(captor.capture());
        assertThat(captor.getValue()).hasSize(3);

        List<String> allSentTokens = captor.getValue().stream()
                .flatMap(p -> p.pushTokens().stream())
                .toList();
        assertThat(allSentTokens).containsExactlyInAnyOrder("ExponentPushToken[1]", "ExponentPushToken[2]", "ExponentPushToken[3]");
    }

    /* markAsRead() 테스트 */

    @DisplayName("기존 푸시 히스토리를 찾아 읽음 처리한다")
    @Test
    void markAsRead_success() {
        // given
        PushHistory history = PushHistory.of(member1.getId(), "title", "body", new HashMap<>());
        pushHistoryRepository.save(history);

        // when
        pushService.markAsRead(history.getId());

        // then
        PushHistory savedHistory = pushHistoryRepository.findById(history.getId()).orElseThrow();
        assertThat(savedHistory.getReadAt()).isNotNull();
    }

    @DisplayName("존재하지 않는 푸시 히스토리 ID로 읽음 처리 시도 시 예외가 발생한다")
    @Test
    void markAsRead_pushHistoryNotFound() {
        // when, then
        assertThatThrownBy(() -> pushService.markAsRead(999L))
                .isInstanceOf(PushHistoryNotFound.class);
    }

    // --- 헬퍼 메소드 ---

    private Device createAndSaveDevice(Member member, String token, String appVersion) {
        Device device = Device.of(member, token, UUID.randomUUID().toString(), SemanticVersion.of(appVersion), "iOS", "26", "iPhone 16");
        return deviceRepository.save(device);
    }

    private PushContent createPushContent() {
        return PushContent.of("[테스트] 내 거친 생각과", "불안한 눈빛과", new HashMap<>());
    }

    private PushContent createPushContent(VersionRange versionRange) {
        return PushContent.of("[테스트] 내 거친 생각과", "불안한 눈빛과", new HashMap<>(), versionRange);
    }

}