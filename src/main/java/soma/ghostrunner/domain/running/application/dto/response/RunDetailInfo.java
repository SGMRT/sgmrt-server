package soma.ghostrunner.domain.running.application.dto.response;

import lombok.Getter;

@Getter
public class RunDetailInfo {

    private Long startedAt;
    private String runningName;
    private String telemetryUrl;
    private Boolean isPublic;

    public RunDetailInfo(Long startedAt, String runningName, String telemetryUrl, Boolean isPublic) {
        this.startedAt = startedAt;
        this.runningName = runningName;
        this.telemetryUrl = telemetryUrl;
        this.isPublic = isPublic;
    }

}
