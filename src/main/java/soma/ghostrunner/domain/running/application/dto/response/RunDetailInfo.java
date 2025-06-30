package soma.ghostrunner.domain.running.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.util.List;

@Getter
public class RunDetailInfo {
    private Long startedAt;
    private String runningName;
    @JsonIgnore
    private String telemetryUrl;
    @Setter
    private List<TelemetryDto> telemetries;

    public RunDetailInfo(Long startedAt, String runningName, String telemetryUrl) {
        this.startedAt = startedAt;
        this.runningName = runningName;
        this.telemetryUrl = telemetryUrl;
    }
}
