package soma.ghostrunner.domain.running.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.api.support.PacemakerType;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class CreatePacemakerRequest {

    @NotNull
    private PacemakerType type;

    @NotNull
    @Positive
    private Double targetDistance;

    @NotNull
    @Positive
    private Integer condition;

    @Min(-50) @Max(50)
    private Integer temperature;

    @NotNull
    private Long courseId;

}
