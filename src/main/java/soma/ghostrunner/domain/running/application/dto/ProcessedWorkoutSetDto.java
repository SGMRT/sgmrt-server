package soma.ghostrunner.domain.running.application.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

@Getter
public class ProcessedWorkoutSetDto {

    private Integer setNum;
    private WorkoutType type;
    private String pace;
    private double startPoint;
    private double endPoint;
    private String feedback;

    @Builder(access = AccessLevel.PRIVATE)
    private ProcessedWorkoutSetDto(Integer setNum, WorkoutType type, String pace, double startPoint, double endPoint) {
        this.setNum = setNum;
        this.type = type;
        this.pace = pace;
        this.startPoint = startPoint;
        this.endPoint = endPoint;
    }

    public static ProcessedWorkoutSetDto of(Integer setNum, WorkoutType type,
                                            String pace, double startPoint, double endPoint) {
        return ProcessedWorkoutSetDto.builder()
                .setNum(setNum)
                .type(type)
                .pace(pace)
                .startPoint(startPoint)
                .endPoint(endPoint)
                .build();
    }

    @Override
    public String toString() {
        return String.format(
                "{ \"setNum\": %d, \"type\": \"%s\", \"pace\": \"%s\", \"startPoint\": %.2f, \"endPoint\": %.2f }",
                setNum, type, pace, startPoint, endPoint
        );
    }

}
