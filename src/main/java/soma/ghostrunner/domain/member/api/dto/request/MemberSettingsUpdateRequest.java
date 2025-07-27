package soma.ghostrunner.domain.member.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberSettingsUpdateRequest {
    private Boolean pushAlarmEnabled;
    private Boolean vibrationEnabled;
    private Boolean doNotDisturbAtNightEnabled;
}
