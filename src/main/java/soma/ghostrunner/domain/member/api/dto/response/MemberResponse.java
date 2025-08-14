package soma.ghostrunner.domain.member.api.dto.response;

import soma.ghostrunner.domain.member.enums.Gender;

public record MemberResponse (
        String uuid,
        String nickname,
        String profilePictureUrl,
        Gender gender,
        Integer age,
        Integer weight,
        Integer height,
        Boolean pushAlarmEnabled,
        Boolean vibrationEnabled,
        Boolean voiceGuidanceEnabled
) { }
