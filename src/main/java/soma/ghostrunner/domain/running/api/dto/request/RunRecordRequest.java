package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunRecordRequest {

    @NotNull @Positive
    private Double distance;    // km

    @NotNull @PositiveOrZero
    private Integer elevationGain;      // m, 고도

    @NotNull @NegativeOrZero
    private Integer elevationLoss;

    @NotNull @PositiveOrZero
    private Long duration;      // 초 단위

    @NotNull @Positive
    private Double avgPace;     // 분/km

    @NotNull @PositiveOrZero
    private Integer calories;

    @NotNull @PositiveOrZero
    private Integer avgBpm;     // bpm

    @NotNull
    @PositiveOrZero
    private Integer avgCadence; // spm

}
