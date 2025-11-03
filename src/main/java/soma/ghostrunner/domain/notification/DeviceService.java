package soma.ghostrunner.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.api.dto.DeviceRegistrationRequest;
import soma.ghostrunner.domain.notification.dao.DeviceRepository;
import soma.ghostrunner.domain.notification.domain.Device;
import soma.ghostrunner.global.error.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final MemberRepository memberRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public void registerDevice(String memberUuid, DeviceRegistrationRequest request) {
        Member member = findMemberOrThrow(memberUuid);
        // todo
    }

    @Deprecated
    @Transactional
    public void saveMemberPushToken(String memberUuid, String pushToken) {
        Member member = findMemberOrThrow(memberUuid);
        validatePushTokenFormat(pushToken);
        boolean exists = deviceRepository.existsByMemberIdAndToken(member.getId(), pushToken);
        if (!exists) {
            log.info("NotificationService: Saving push token {} for member uuid {}", pushToken, memberUuid);
            Device device = new Device(member, pushToken);
            deviceRepository.save(device);
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
