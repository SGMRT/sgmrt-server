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

}
