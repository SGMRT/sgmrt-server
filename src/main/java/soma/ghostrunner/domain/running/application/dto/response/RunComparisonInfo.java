package soma.ghostrunner.domain.running.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunComparisonInfo {
    private Double distance;
    private Long duration;
    private Integer cadence;
    private Double pace;

    public RunComparisonInfo(RunRecordInfo myRecord, RunRecordInfo ghostRecord) {
        this.distance = myRecord.getDistance() - ((myRecord.getDistance() * myRecord.getAveragePace()) / ghostRecord.getAveragePace());
        this.duration = myRecord.getDuration() - ghostRecord.getDuration();
        this.cadence = myRecord.getCadence() - ghostRecord.getCadence();
        this.pace = myRecord.getAveragePace() - ghostRecord.getAveragePace();
    }
}
