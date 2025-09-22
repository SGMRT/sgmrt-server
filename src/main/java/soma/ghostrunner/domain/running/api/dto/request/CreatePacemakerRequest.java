package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class CreatePacemakerRequest {

    @NotEmpty
    private String purpose;

    @NotNull
    @Positive
    private Double targetDistance;

    @NotNull
    @Positive
    private Integer condition;

    @Min(-50) @Max(50)
    private Integer temperature;

    @Positive
    private Double pacePerKm;

    @NotNull
    private LocalDate localDate;

}
