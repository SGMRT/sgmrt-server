package soma.ghostrunner.domain.running.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunRecordDto {

    private Double distance;
    private Double elevationGain;
    private Double elevationLoss;
    private Long duration;
    private Double avgPace;
    private Integer calories;
    private Integer avgBpm;
    private Integer avgCadence;

}
