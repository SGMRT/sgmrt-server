package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class TelemetryDto {
    @NotNull
    private Long timeStamp;

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    @NotNull @PositiveOrZero
    private Double dist;    // 누적거리 (m)

    @NotNull @Positive
    private Double pace;    // 현재 페이스 (분/km)

    @NotNull @PositiveOrZero
    private Integer alt;    // 현재 고도 (m)

    @NotNull @PositiveOrZero
    private Integer cadence;

    @NotNull @PositiveOrZero
    private Integer bpm;

    @NotNull
    private Boolean isRunning;
}
