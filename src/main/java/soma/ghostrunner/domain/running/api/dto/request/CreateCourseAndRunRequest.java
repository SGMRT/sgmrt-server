package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import soma.ghostrunner.domain.running.api.validation.NoPauseForPublic;

import java.util.List;

@NoPauseForPublic(
        hasPaused = "hasPaused",
        isPublic = "isPublic",
        message = "중지한 기록이 있다면 공개 설정이 불가능합니다."
)
@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class CreateCourseAndRunRequest {

    @NotBlank
    private String runningName;

    @NotNull
    private Long startedAt;

    @NotNull
    @Valid
    private RunRecordRequest record;

    @NotNull
    private Boolean hasPaused;

    @NotNull
    private Boolean isPublic;

    @NotNull @Valid
    private List<TelemetryRequest> telemetries;

}
