package soma.ghostrunner.domain.pacemaker.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import soma.ghostrunner.domain.pacemaker.api.support.PacemakerType;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class PacemakerCreateCommand {

    private String type;
    private Double targetDistance;
    private Integer condition;
    private Integer temperature;
    private LocalDate localDate;
    private Long courseId;

    public PacemakerCreateCommand(PacemakerType pacemakerType, Double targetDistance,
                                  Integer condition, Integer temperature, Long courseId) {
        this.type = pacemakerType.name();
        this.targetDistance = targetDistance;
        this.condition = condition;
        this.temperature = temperature;
        this.localDate = LocalDate.now();
        this.courseId = courseId;
    }

}
