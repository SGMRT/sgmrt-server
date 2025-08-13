package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TelemetryDto {

    private Long timeStamp;
    private Double lat;
    private Double lng;
    private Double dist;
    private Double pace;
    private Double alt;
    private Integer cadence;
    private Integer bpm;
    private Boolean isRunning;

    public void setRelativeTimeStamp(Long timeStamp) {
        this.timeStamp = timeStamp;
    }

}
