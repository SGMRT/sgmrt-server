package soma.ghostrunner.domain.device.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import soma.ghostrunner.domain.device.dao.DeviceRepository;
import soma.ghostrunner.domain.device.domain.Device;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.device.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.global.common.versioning.SemanticVersion;
import soma.ghostrunner.global.common.versioning.VersionRange;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final MemberRepository memberRepository;
    private final DeviceRepository deviceRepository;

    public List<Device> findDevicesByMemberIdsAndAppVersions(List<Long> memberIds, VersionRange versionRange) {
        return deviceRepository.findAllByMemberIdsAndAppVersionRange(memberIds, versionRange);
    }

    public List<Device> findDevicesByAppVersions(VersionRange versionRange) {
        return deviceRepository.findAllByAppVersionRange(versionRange);
    }

    public List<Device> findDevicesByMemberIds(List<Long> memberIds) {
        return deviceRepository.findByMemberIdIn(memberIds);
    }

    @Transactional
    public void registerDevice(String memberUuid, DeviceRegistrationRequest request) {
        Assert.notNull(request.getDeviceUuid(), "Device UUID는 필수입니다.");
        Member member = findMemberOrThrow(memberUuid);
        Optional<Device> optionalDevice = deviceRepository.findByUuid(request.getDeviceUuid());
        if (optionalDevice.isPresent()) {
            // 주어진 uuid의 기기 정보가 존재하는 경우 기존 Device 정보 업데이트 (member_id 포함)
            Device device = optionalDevice.get();
            Set<String> updatedFields = device.updateInfo(member, request);
            logUpdatedFieldsIfUpdated(memberUuid, request, updatedFields);
        } else {
            // 기기 정보가 존재하지 않는 경우 새로 저장
            createAndSaveDevice(request, member);
            log.info("새로운 디바이스 정보 저장: 회원 uuid='{}', 기기 uuid='{}', 요청='{}'", memberUuid, request.getDeviceUuid(), request);
        }
    }

    private void createAndSaveDevice(DeviceRegistrationRequest request, Member member) {
        Device device = Device.of(member, request.getPushToken(), request.getDeviceUuid(), SemanticVersion.of(request.getAppVersion()),
                request.getOsName(), request.getOsVersion(), request.getModelName());
        deviceRepository.save(device);
    }

    private static void logUpdatedFieldsIfUpdated(String memberUuid, DeviceRegistrationRequest request, Set<String> updatedFields) {
        if(!updatedFields.isEmpty()) {
            log.info("다음 UUID 기기에 대한 디바이스 정보 변경. 변경된 필드={}, 회원 uuid='{}', 기기 uuid='{}', 요청='{}'",
                    updatedFields, memberUuid, request.getDeviceUuid(), request);
        }
    }

    @Deprecated(since = "v1.0.4 PushToken 저장 방식 변경으로 인한 사용 중단; registerDevice 활용 (클라이언트 하위호환을 위해 남겨둠)")
    @Transactional
    public void saveMemberPushToken(String memberUuid, String pushToken) {
        // todo 분산락으로 동일 토큰에 대한 따닥 접근 처리 고려
        Member member = findMemberOrThrow(memberUuid);
        validatePushTokenFormat(pushToken);
        Optional<Device> existingDeviceOpt = deviceRepository.findByToken(pushToken);
        if (existingDeviceOpt.isEmpty()) {
            // 토큰이 존재하지 않는다면 Device 저장
            deviceRepository.save(Device.of(member, pushToken));
            log.info("새로운 푸쉬 토큰 저장: 회원 uuid={}, 토큰={}", memberUuid, pushToken);
        } else {
            // 푸쉬 토큰이 이미 존재한다면 기존 Device 정보 삭제 후 저장 (Expo 기준 동일한 기기 == 동일한 푸쉬토큰이므로 동일한 기기에서 회원 전환한 경우임)
            Device oldDevice = existingDeviceOpt.get();
            if (oldDevice.getMember() != null && oldDevice.getMember().getId().equals(member.getId())) {
                // 동일 회원이 동일 토큰을 재저장하는 경우라면 별도 처리 없음
                return;
            }
            deviceRepository.deleteById(oldDevice.getId());
            deviceRepository.save(Device.of(member, pushToken));
            log.info("푸쉬 토큰이 이미 존재하여 회원 변경 처리: 기존 회원 uuid={}, 새로운 회원 uuid={}, 토큰={}",
                    oldDevice.getMember() == null ? "null" : oldDevice.getMember().getUuid(), memberUuid, pushToken);
        }
    }

    private void validatePushTokenFormat(String pushToken) {
        if (pushToken == null || !pushToken.startsWith("ExponentPushToken[")) {
            throw new IllegalArgumentException("올바른 Push Token 방식이 아닙니다: " + pushToken);
        }
    }

    private Member findMemberOrThrow(String uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
    }

}
