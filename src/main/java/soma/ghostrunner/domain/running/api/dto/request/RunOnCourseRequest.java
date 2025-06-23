package soma.ghostrunner.domain.running.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import soma.ghostrunner.domain.running.api.validation.NoPauseForPublic;
import soma.ghostrunner.domain.running.api.validation.ValidateRunningMode;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.global.common.validator.enums.EnumValid;

import java.time.LocalDateTime;
import java.util.List;

@NoPauseForPublic(
        hasPaused = "hasPaused",
        isPublic = "isPublic",
        message = "중지한 기록이 있다면 공개 설정이 불가능합니다."
)
@ValidateRunningMode(
        modeField = "mode",
        ghostRunningId = "ghostRunningId",
        message = "러닝 모드에 따라 고스트 ID 값의 규칙이 지켜지지 않았습니다."
)
@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class RunOnCourseRequest {

    @NotNull
    @EnumValid(enumClass = RunningMode.class, message = "유효하지 않은 러닝모드입니다.", ignoreCase = true)
    private String mode;

    private Long ghostRunningId;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime startedAt;

    @NotNull
    @Valid
    private RunRecordDto record;

    @NotNull
    private Boolean hasPaused;

    @NotNull
    private Boolean isPublic;

    @NotNull @Valid
    private List<TelemetryDto> telemetries;
}
