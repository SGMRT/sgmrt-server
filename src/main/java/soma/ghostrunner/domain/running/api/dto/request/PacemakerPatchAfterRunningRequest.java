package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PacemakerPatchAfterRunningRequest {

    @NotNull
    private Long pacemakerId;

    @NotNull
    private Long runningId;

}
