package soma.ghostrunner.domain.running.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
public class CreatePacemakerCommand {

    private String purpose;
    private Double targetDistance;
    private Integer condition;
    private Integer temperature;
    private Double pacePerKm;
    private LocalDate localDate;

}
