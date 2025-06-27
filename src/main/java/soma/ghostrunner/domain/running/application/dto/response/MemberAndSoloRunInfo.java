package soma.ghostrunner.domain.running.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MemberAndSoloRunInfo {
    private String nickname;
    private String profileUrl;
    private RunRecordInfo recordInfo;
}
