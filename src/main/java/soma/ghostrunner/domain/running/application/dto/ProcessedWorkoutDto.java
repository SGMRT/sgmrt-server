package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

@Getter
@AllArgsConstructor
public class ProcessedWorkoutDto {

    private Integer setNum;
    private WorkoutType type;
    private double startPoint;
    private double endPoint;

    @Override
    public String toString() {
        return String.format(
                "{ \"setNum\": %d, \"type\": \"%s\", \"startPoint\": %.2f, \"endPoint\": %.2f }",
                setNum, type, startPoint, endPoint
        );
    }

}
