package soma.ghostrunner.domain.running.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import soma.ghostrunner.domain.running.api.support.PacemakerType;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class CreatePacemakerCommand {

    private String type;
    private Double targetDistance;
    private Integer condition;
    private Integer temperature;
    private LocalDate localDate;
    private Long courseId;

    public CreatePacemakerCommand(PacemakerType pacemakerType, Double targetDistance,
                                  Integer condition, Integer temperature, Long courseId) {
        this.type = pacemakerType.name();
        this.targetDistance = targetDistance;
        this.condition = condition;
        this.temperature = temperature;
        this.localDate = LocalDate.now();
        this.courseId = courseId;
    }

}
