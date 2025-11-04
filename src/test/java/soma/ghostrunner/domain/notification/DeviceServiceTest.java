package soma.ghostrunner.domain.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.domain.notification.application.DeviceService;
import soma.ghostrunner.domain.notification.dao.DeviceRepository;
import soma.ghostrunner.domain.notification.domain.Device;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DeviceService 통합 테스트")
class DeviceServiceTest extends IntegrationTestSupport {

    @Autowired
    DeviceService deviceService;
    @Autowired MemberRepository memberRepository;
    @Autowired DeviceRepository deviceRepository;

    private Member member;
    private Device device;

    @BeforeEach
    void setUp() {
        member = Member.of("카리나", "profile-url");
        memberRepository.save(member);
        device = Device.of(member, "ExponentPushToken[xxxx]");
        deviceRepository.save(device);
    }

    // findDevicesByMemberIds 테스트
    @DisplayName("주어진 회원 ID 목록에 해당하는 디바이스 정보를 조회한다.")
    @Test
    void findDevicesByMemberIds_success() {
        // given
        Member anotherMember = Member.of("윈터", "another-profile-url");
        memberRepository.save(anotherMember);
        List<Long> memberIds = List.of(member.getId(), anotherMember.getId());

        // when
        List<Device> devices = deviceService.findDevicesByMemberIds(memberIds);

        // then
        assertThat(devices).isNotEmpty();
        assertThat(devices).extracting("member.id")
                .containsExactlyInAnyOrder(member.getId(), anotherMember.getId());
    }

    @DisplayName("주어진 회원 ID 목록에 해당하는 디바이스 정보가 없으면 빈 리스트를 반환한다.")
    @Test
    void findDevicesByMemberIds_noDevices() {
        // given
        List<Long> notFoundMemberIds = List.of(1000L, 1001L);
        // when
        List<Device> devices = deviceService.findDevicesByMemberIds(notFoundMemberIds);
        // then
        assertThat(devices).isEmpty();
    }

    // registerDevice 테스트
    @DisplayName("새로운 디바이스 정보를 저장한다.")
    @Test
    void registerDevice_success() {
        // given
        String pushToken = "ExponentPushToken[yyyy]";
        DeviceRegistrationRequest request = createDeviceRequest("device-uuid", pushToken);

        // when
        deviceService.registerDevice(member.getUuid(), request);

        // then
        List<Device> devices = deviceRepository.findAll();
        Device device = deviceRepository.findByUuid("device-uuid").orElseThrow();
        assertThat(device.getMember().getUuid()).isEqualTo(member.getUuid());
        assertThat(device.getUuid()).isEqualTo("device-uuid");
        assertThat(device.getToken()).isEqualTo(pushToken);
        assertThat(device.getAppVersion()).isEqualTo(request.getAppVersion());
        assertThat(device.getOsName()).isEqualTo(request.getOsName());
        assertThat(device.getOsVersion()).isEqualTo(request.getOsVersion());
        assertThat(device.getModelName()).isEqualTo(request.getModelName());
    }

    @DisplayName("푸쉬 토큰 형식이 Expo Push Token이 아니면 예외를 발생시킨다.")
    @Test
    void registerDevice_invalidFormat() {
        // given
        String invalidPushToken = "i-am-invalid";
        DeviceRegistrationRequest request = createDeviceRequest("device-uuid", invalidPushToken);

        // when & then
        assertThatThrownBy(() -> deviceService.registerDevice(member.getUuid(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 Push Token 방식이 아닙니다");
    }

    @DisplayName("동일한 기기 UUID의 디바이스 정보가 존재한다면 기존 정보를 업데이트한다.")
    @Test
    void registerDevice_updateDevice() {
        // given
        String pushToken = "ExponentPushToken[update-me]";
        Device device = Device.of(member, pushToken, "device-uuid", "0.0.1", "Android", "21", "Samsung Galaxy S25");
        deviceRepository.save(device);
        Member newMember = Member.of("지젤", "new-profile-url");
        memberRepository.save(newMember);
        long beforeCount = deviceRepository.count();

        // when
        DeviceRegistrationRequest request = createDeviceRequest("device-uuid", pushToken);
        deviceService.registerDevice(newMember.getUuid(), request);

        // then
        long afterCount = deviceRepository.count();
        assertThat(afterCount).isEqualTo(beforeCount);
        Device updatedDevice = deviceRepository.findByToken(pushToken).orElseThrow();
        assertThat(updatedDevice.getUuid()).isEqualTo("device-uuid");
        assertThat(updatedDevice.getAppVersion()).isEqualTo(request.getAppVersion());
        assertThat(updatedDevice.getOsName()).isEqualTo(request.getOsName());
        assertThat(updatedDevice.getOsVersion()).isEqualTo(request.getOsVersion());
        assertThat(updatedDevice.getModelName()).isEqualTo(request.getModelName());
    }

    @DisplayName("존재하지 않는 회원에 포쉬 토큰 저장 시 예외를 발생시킨다.")
    @Test
    void registerDevice_memberNotFound() {
        // given
        String nonExistentUuid = "not-found-member-uuid";
        String newPushToken = "ExponentPushToken[yyyy]";
        DeviceRegistrationRequest request = createDeviceRequest(nonExistentUuid, newPushToken);

        // when & then
        assertThatThrownBy(() -> deviceService.registerDevice(nonExistentUuid, request))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @DisplayName("기기 UUID가 null인 경우 예외를 발생시킨다.")
    @Test
    void registerDevice_nullDeviceUuid() {
        // given
        String pushToken = "ExponentPushToken[yyyy]";
        DeviceRegistrationRequest request = createDeviceRequest(null, pushToken);

        // when & then
        assertThatThrownBy(() -> deviceService.registerDevice(member.getUuid(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device UUID는 필수입니다.");
    }

    // saveMemberToken 테스트 (deprecated 메서드)

    @DisplayName("새로운 푸쉬 토큰을 저장한다.")
    @Deprecated(since = "v1.0.4 PushToken 저장 방식 변경으로 인한 사용 중단; registerDevice 활용 (클라이언트 하위호환을 위해 남겨둠)")
    @Test
    void saveMemberPushToken_newToken() {
        // given
        String newPushToken = "ExponentPushToken[yyyy]";

        // when
        deviceService.saveMemberPushToken(member.getUuid(), newPushToken);

        // then
        boolean exists = deviceRepository.existsByMemberIdAndToken(member.getId(), newPushToken);
        assertThat(exists).isTrue();
    }

    @DisplayName("동일한 푸쉬토큰의 디바이스 정보가 존재한다면 기존 엔티티를 삭제하고 새로 덮어쓴다.")
    @Deprecated(since = "v1.0.4 PushToken 저장 방식 변경으로 인한 사용 중단; registerDevice 활용 (클라이언트 하위호환을 위해 남겨둠)")
    @Test
    void saveMemberPushToken_duplicateToken() {
        // given
        String existingPushToken = device.getToken();
        Member newMember = Member.of("윈터", "profile-url");
        memberRepository.save(newMember);
        long initialCount = deviceRepository.count();

        // when
        deviceService.saveMemberPushToken(newMember.getUuid(), existingPushToken);

        // then
        long afterCount = deviceRepository.count();
        assertThat(afterCount).isEqualTo(initialCount);
        Device updatedDevice = deviceRepository.findByToken(existingPushToken).orElseThrow();
        assertThat(updatedDevice.getMember().getUuid()).isEqualTo(newMember.getUuid());
        assertThat(updatedDevice.getToken()).isEqualTo(existingPushToken);
    }

    @DisplayName("푸쉬 토큰 형식이 Expo Push Token이 아니면 예외를 발생시킨다.")
    @Deprecated(since = "v1.0.4 PushToken 저장 방식 변경으로 인한 사용 중단; registerDevice 활용 (클라이언트 하위호환을 위해 남겨둠)")
    @Test
    void saveMemberPushToken_invalidFormat() {
        // given
        String invalidPushToken = "i-am-invalid";

        // when & then
        assertThatThrownBy(() -> deviceService.saveMemberPushToken(member.getUuid(), invalidPushToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 Push Token 방식이 아닙니다");
    }

    @DisplayName("존재하지 않는 회원에 포쉬 토큰 저장 시 예외를 발생시킨다.")
    @Deprecated(since = "v1.0.4 PushToken 저장 방식 변경으로 인한 사용 중단; registerDevice 활용 (클라이언트 하위호환을 위해 남겨둠)")
    @Test
    void saveMemberPushToken_memberNotFound() {
        // given
        String nonExistentUuid = "not-found-member-uuid";
        String newPushToken = "ExponentPushToken[yyyy]";

        // when & then
        assertThatThrownBy(() -> deviceService.saveMemberPushToken(nonExistentUuid, newPushToken))
                .isInstanceOf(MemberNotFoundException.class);
    }

    // ----- 헬퍼 메소드 -----

    private DeviceRegistrationRequest createDeviceRequest(String deviceUuid, String pushToken) {
        return new DeviceRegistrationRequest(
                deviceUuid,
                "1.0.0",
                pushToken,
                "iOS",
                "18.4",
                "iPhone 16 Pro Max"
        );
    }



}