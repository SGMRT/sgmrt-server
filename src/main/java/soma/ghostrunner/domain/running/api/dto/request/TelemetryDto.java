package soma.ghostrunner.domain.running.api.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class TelemetryDto {
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime timeStamp;

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
