package soma.ghostrunner.domain.notification.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.domain.Device;
import soma.ghostrunner.global.common.versioning.SemanticVersion;
import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PushTokenRepository 통합 테스트")
class DeviceRepositoryTest extends IntegrationTestSupport {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 테스트용 Member 생성 헬퍼 메서드
    private Member createMember(String nickname) {
        return Member.of(nickname, "test_profile_url");
    }

    // 테스트용 Device 생성 헬퍼 메서드
    private Device createDevice(Member member, String token) {
        return Device.of(member, token);
    }

    @DisplayName("특정 회원의 디바이스 정보가 존재하는지 확인한다.")
    @Test
    void existsByMemberIdAndToken_success() {
        // given
        Member member = createMember("신짱구");
        memberRepository.save(member);
        Device device = createDevice(member, "ExponentPushToken[123]");
        deviceRepository.save(device);

        // when
        boolean exists = deviceRepository.existsByMemberIdAndToken(member.getId(), "ExponentPushToken[123]");

        // then
        assertThat(exists).isTrue();
    }

    @DisplayName("특정 회원의 디바이스 정보가 존재하지 않으면 false를 반환한다.")
    @Test
    void existsByMemberIdAndToken_notExists() {
        // given
        Member member = createMember("신짱구");
        memberRepository.save(member);
        Device device = createDevice(member, "ExponentPushToken[123]");
        deviceRepository.save(device);

        // when
        boolean exists = deviceRepository.existsByMemberIdAndToken(member.getId(), "non-existent-token");

        // then
        assertThat(exists).isFalse();
    }

    @DisplayName("다른 회원의 디바이스 저장에 대해 조회하면 false를 반환한다.")
    @Test
    void existsByMemberIdAndToken_failure_byMember() {
        // given
        Member member1 = createMember("신짱구");
        Member member2 = createMember("나훈아");
        memberRepository.saveAll(List.of(member1, member2));
        Device device1 = createDevice(member1, "ExponentPushToken[123]");
        deviceRepository.save(device1);

        // when
        boolean exists = deviceRepository.existsByMemberIdAndToken(member2.getId(), "ExponentPushToken[123]");

        // then
        assertThat(exists).isFalse();
    }

    @DisplayName("여러 회원의 ID로 디바이스 목록을 조회한다.")
    @Test
    void findByMemberIdIn_success() {
        // given
        Member member1 = createMember("짱구");
        Member member2 = createMember("흰둥이");
        Member member3 = createMember("짱아");
        memberRepository.saveAll(List.of(member1, member2, member3));

        // member1은 두 개의 디바이스를 갖는다.
        Device token1 = createDevice(member1, "ExponentPushToken[1]");
        Device token2 = createDevice(member1, "ExponentPushToken[2]");
        Device token3 = createDevice(member2, "ExponentPushToken[3]");
        Device token4 = createDevice(member3, "ExponentPushToken[4]");
        deviceRepository.saveAll(List.of(token1, token2, token3, token4));

        // when
        List<Long> targetMemberIds = List.of(member1.getId(), member2.getId()); // member1과 member2의 토큰을 조회한다.
        List<Device> foundDevices = deviceRepository.findByMemberIdIn(targetMemberIds);

        // then
        assertThat(foundDevices).hasSize(3)
                .extracting("member.nickname", "token")
                .containsExactlyInAnyOrder(
                        tuple("짱구", "ExponentPushToken[1]"),
                        tuple("짱구", "ExponentPushToken[2]"),
                        tuple("흰둥이", "ExponentPushToken[3]")
                );
    }

    @DisplayName("존재하지 않는 회원 ID를 포함하여 조회하면 존재하는 디바이스 정보만 반환한다.")
    @Test
    void findByMemberIdIn_mixedExistence() {
        // given
        Member member1 = createMember("신형만");
        Member member2 = createMember("봉미선");
        memberRepository.saveAll(List.of(member1, member2));

        Device token1 = createDevice(member1, "ExponentPushToken[1]");
        Device token2 = createDevice(member2, "ExponentPushToken[2]");
        deviceRepository.saveAll(List.of(token1, token2));

        // when
        List<Long> targetMemberIds = List.of(member1.getId(), 9999L); // 9999L는 존재하지 않는 ID
        List<Device> foundTokens = deviceRepository.findByMemberIdIn(targetMemberIds);

        // then
        // user1만 조회된다.
        assertThat(foundTokens).hasSize(1)
                .extracting("member.nickname", "token")
                .containsExactly(tuple("신형만", "ExponentPushToken[1]"));
    }

    @DisplayName("디바이스 조회에 빈 ID 리스트를 인자로 넘기면 빈 결과가 반환된다.")
    @Test
    void findByMemberIdIn_emptyInput() {
        // given
        List<Long> emptyList = Collections.emptyList();

        // when
        List<Device> foundDevices = deviceRepository.findByMemberIdIn(emptyList);

        // then
        assertThat(foundDevices).isEmpty();
    }

    @DisplayName("디바이스 UUID로 디바이스를 조회한다.")
    @Test
    void findByUuid_success() {
        // given
        Member member = createMember("나훈아");
        memberRepository.save(member);
        Device device = createDevice(member, "테스트-uuid");
        deviceRepository.save(device);

        // when
        Device foundDevice = deviceRepository.findByUuid(device.getUuid()).orElseThrow();

        // then
        assertThat(foundDevice.getToken()).isEqualTo("테스트-uuid");
        assertThat(foundDevice.getMember().getNickname()).isEqualTo("나훈아");
    }

    @DisplayName("디바이스 UUID를 NULL로 디바이스를 조회한다.")
    @Test
    void findByUuid_findByNull() {
        // given
        Member member = createMember("나훈아");
        memberRepository.save(member);
        Device device = createDevice(member, "테스트-uuid");
        deviceRepository.save(device);

        // when
        Device foundDevice = deviceRepository.findByUuid(device.getUuid()).orElseThrow();

        // then
        assertThat(foundDevice.getToken()).isEqualTo("테스트-uuid");
        assertThat(foundDevice.getMember().getNickname()).isEqualTo("나훈아");
    }

    @DisplayName("존재하지 않는 디바이스 UUID로 조회하면 빈 Optional을 반환한다.")
    @Test
    void findByUuid_notExists() {
        // given
        String nonExistentUuid = "없는-uuid";

        // when
        Optional<Device> optionalDevice = deviceRepository.findByUuid(nonExistentUuid);

        // then
        assertThat(optionalDevice).isEmpty();
    }

    @DisplayName("특정 푸시 토큰이 존재하는지 확인한다.")
    @Test
    void existsByToken_success() {
        // given
        Member member = createMember("유리");
        memberRepository.save(member);
        Device device = createDevice(member, "unique-token");
        deviceRepository.save(device);

        // when
        boolean exists = deviceRepository.existsByToken("unique-token");

        // then
        assertThat(exists).isTrue();
    }

    @DisplayName("존재하지 않는 푸시 토큰에 대해 존재 여부를 확인하면 false를 반환한다.")
    @Test
    void existsByToken_notExists() {
        // given
        String nonExistentToken = "없는-uuid";
        // when
        boolean exists = deviceRepository.existsByToken(nonExistentToken);
        // then
        assertThat(exists).isFalse();
    }

    @DisplayName("특정 푸시 토큰으로 디바이스 정보를 조회한다.")
    @Test
    void findByToken_success() {
        // given
        Member member = createMember("철수");
        memberRepository.save(member);

        Device device = createDevice(member, "push-token");
        deviceRepository.save(device);

        // when
        Optional<Device> optionalDevice = deviceRepository.findByToken("push-token");

        // then
        assertThat(optionalDevice).isPresent();
        assertThat(optionalDevice.get().getToken()).isEqualTo("push-token");
        assertThat(optionalDevice.get().getMember().getNickname()).isEqualTo("철수");
    }

    @DisplayName("회원 ID 및 버전 범위 조건으로 디바이스 목록을 조회한다.)")
    @ParameterizedTest(name = "[{index}] {0} 조건 테스트")
    @MethodSource("versionRangeTestCases")
    void findDevicesByMemberIdsAndAppVersions(
            String testName,
            VersionRange versionRange,
            List<String> expectedTokens
    ) {
        // given
        Member member1 = createMember("도라에몽");
        Member member2 = createMember("진구");
        memberRepository.saveAll(List.of(member1, member2));

        Device device1 = Device.of(member1, "ExponentPushToken[1]", "uuid-1", SemanticVersion.of("1.0.0"), "iOS", "26.0", "iPhone 16 Pro");
        Device device2 = Device.of(member1, "ExponentPushToken[2]", "uuid-2", SemanticVersion.of("1.0.3"), "iOS", "16.0", "iPhone 15");
        Device device3 = Device.of(member2, "ExponentPushToken[3]", "uuid-3", SemanticVersion.of("1.5.0"), "iOS", "13.0", "iPhone 8");
        Device device4 = Device.of(member2, "ExponentPushToken[4]", "uuid-4", SemanticVersion.of("2.0.0"), "Android", "21.0", "Galaxy S25");
        deviceRepository.saveAll(List.of(device1, device2, device4, device3));

        List<Long> memberIds = List.of(member1.getId(), member2.getId());

        // when
        List<Device> foundDevices = deviceRepository.findAllByMemberIdsAndAppVersionRange(memberIds, versionRange);

        // then
        assertThat(foundDevices).hasSize(expectedTokens.size())
                .extracting("uuid")
                .containsExactlyInAnyOrderElementsOf(expectedTokens);
    }

    private static Stream<Arguments> versionRangeTestCases() {
        return Stream.of(
                Arguments.of("1.0.1 이상 (GTE)", VersionRange.parse("1.0.3^"), List.of("uuid-2", "uuid-3", "uuid-4")), // 1.0.3, 1.5.0, 2.0.0
                Arguments.of("1.5.0 이하 (LTE)", VersionRange.parse("1.5.0v"), List.of("uuid-1", "uuid-2", "uuid-3")), // 1.0.0, 1.0.3, 1.5.0
                Arguments.of("1.0.0 일치 (EQUALS)", VersionRange.parse("1.0.0"), List.of("uuid-1")), // 1.0.0
                Arguments.of("1.5.0 초과 (GT)", VersionRange.of(SemanticVersion.of("1.5.0"), VersionRange.Operator.GREATER_THAN), List.of("uuid-4")), // 2.0.0
                Arguments.of("2.0.0 미만 (LT)", VersionRange.of(SemanticVersion.of("2.0.0"), VersionRange.Operator.LESS_THAN), List.of("uuid-1", "uuid-2", "uuid-3")), // 1.0.0, 1.0.3, 1.5.0
                Arguments.of("9.9.9 일치 (EQUALS)", VersionRange.parse("9.9.9"), List.of()), // 0건
                Arguments.of("0.0.0 이상 (GTE)", VersionRange.ALL_VERSIONS, List.of("uuid-1", "uuid-2", "uuid-3", "uuid-4")) // 3건
        );
    }

    @DisplayName("버전 범위가 null로 주어지면 모든 버전의 디바이스를 조회한다.")
    @Test
    void findDevicesByMemberIdsAndAppVersions_nullRange() {
        // given
        Member member1 = createMember("도라에몽");
        Member member2 = createMember("진구");
        memberRepository.saveAll(List.of(member1, member2));

        Device device1 = Device.of(member1, "ExponentPushToken[1]", "uuid-1", SemanticVersion.of("1.0.0"), "iOS", "14.0", "iPhone 8");
        Device device2 = Device.of(member1, "ExponentPushToken[2]", "uuid-2", SemanticVersion.of("2.0.0"), "Android", "11.0", "Galaxy S25");
        Device device3 = Device.of(member2, "ExponentPushToken[3]", "uuid-3", SemanticVersion.of("1.5.0"), "iOS", "13.0", "iPhone 16 Pro");
        deviceRepository.saveAll(List.of(device1, device2, device3));

        List<Long> memberIds = List.of(member1.getId(), member2.getId());

        // when
        List<Device> foundDevices = deviceRepository.findAllByMemberIdsAndAppVersionRange(memberIds, null);

        // then
        assertThat(foundDevices).hasSize(3)
                .extracting("token")
                .containsExactlyInAnyOrder("ExponentPushToken[1]", "ExponentPushToken[2]", "ExponentPushToken[3]");
    }

    @DisplayName("버전 범위가 ALL_VERSIONS로 주어지면 모든 버전의 디바이스를 조회한다.")
    @Test
    void findDevicesByMemberIdsAndAppVersions_ALL_VERSIONS_Range() {
        // given
        Member member1 = createMember("도라에몽");
        Member member2 = createMember("진구");
        memberRepository.saveAll(List.of(member1, member2));

        Device device1 = Device.of(member1, "ExponentPushToken[1]", "uuid-1", SemanticVersion.of("0.0.0"), "iOS", "14.0", "iPhone 8");
        Device device2 = Device.of(member1, "ExponentPushToken[2]", "uuid-2", SemanticVersion.of("1.0.0"), "Android", "11.0", "Galaxy S25");
        Device device3 = Device.of(member2, "ExponentPushToken[3]", "uuid-3", SemanticVersion.of("3.5.10"), "iOS", "13.0", "iPhone 16 Pro");
        deviceRepository.saveAll(List.of(device1, device2, device3));

        List<Long> memberIds = List.of(member1.getId(), member2.getId());

        // when
        VersionRange allVersionsRange = VersionRange.ALL_VERSIONS;
        List<Device> foundDevices = deviceRepository.findAllByMemberIdsAndAppVersionRange(memberIds, allVersionsRange);

        // then
        assertThat(foundDevices).hasSize(3)
                .extracting("token")
                .containsExactlyInAnyOrder("ExponentPushToken[1]", "ExponentPushToken[2]", "ExponentPushToken[3]");
    }

    @DisplayName("주어진 버전 범위 내의 디바이스들을 모두 조회한다.")
    @Test
    void findAllByAppVersionRange_success() {
        // given
        Member member1 = createMember("도라에몽");
        Member member2 = createMember("진구");
        memberRepository.saveAll(List.of(member1, member2));

        Device device1 = Device.of(member1, "ExponentPushToken[1]", "uuid-1", SemanticVersion.of("1.0.0"), "iOS", "14.0", "iPhone 8");
        Device device2 = Device.of(member1, "ExponentPushToken[2]", "uuid-2", SemanticVersion.of("1.0.5"), "Android", "11.0", "Galaxy S25");
        Device device3 = Device.of(member2, "ExponentPushToken[3]", "uuid-3", SemanticVersion.of("2.0.0"), "iOS", "13.0", "iPhone 16 Pro");
        deviceRepository.saveAll(List.of(device1, device2, device3));

        // when
        VersionRange versionRange = VersionRange.atLeast(SemanticVersion.of("1.0.3"));
        List<Device> foundDevices = deviceRepository.findAllByAppVersionRange(versionRange);

        // then
        assertThat(foundDevices).hasSize(2)
                .extracting("token")
                .containsExactlyInAnyOrder("ExponentPushToken[2]", "ExponentPushToken[3]");
    }

}