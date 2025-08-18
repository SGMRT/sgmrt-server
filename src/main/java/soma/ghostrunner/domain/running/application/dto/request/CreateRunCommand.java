package soma.ghostrunner.domain.running.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CreateRunCommand {

    private String runningName;
    private Long ghostRunningId;
    private String mode;
    private Long startedAt;
    private RunRecordCommand record;
    private Boolean hasPaused;
    private Boolean isPublic;

    public void subtractInitialElevation(Double initialElevation) {
        BigDecimal currentElevationGain = BigDecimal.valueOf(this.getRecord().getElevationGain());
        BigDecimal currentElevationLoss = BigDecimal.valueOf(this.getRecord().getElevationLoss());

        BigDecimal relativeElevationGain = currentElevationGain.subtract(BigDecimal.valueOf(initialElevation));
        BigDecimal relativeElevationLoss = currentElevationLoss.subtract(BigDecimal.valueOf(initialElevation));

        this.record.setElevationGain(Double.valueOf(relativeElevationGain.toString()));
        this.record.setElevationLoss(Double.valueOf(relativeElevationLoss.toString()));
    }

}
