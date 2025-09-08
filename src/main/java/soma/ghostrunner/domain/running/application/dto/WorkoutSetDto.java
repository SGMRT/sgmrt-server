package soma.ghostrunner.domain.running.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

@Getter
public class WorkoutSetDto {

    private Integer setNum;
    private WorkoutType type;

    @JsonProperty("pace_min/km")
    private String pace;

    @JsonProperty("start_km")
    private double startPoint;

    @JsonProperty("end_km")
    private double endPoint;

    private String feedback;
    private String message;

    @Builder(access = AccessLevel.PRIVATE)
    private WorkoutSetDto(Integer setNum, WorkoutType type, String pace,
                          double startPoint, double endPoint, String feedback) {
        this.setNum = setNum;
        this.type = type;
        this.pace = pace;
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.feedback = feedback;

    }

    public static WorkoutSetDto of(Integer setNum, WorkoutType type,
                                   String pace, double startPoint, double endPoint) {
        return WorkoutSetDto.builder()
                .setNum(setNum)
                .type(type)
                .pace(pace)
                .startPoint(startPoint)
                .endPoint(endPoint)
                .build();
    }

    public static WorkoutSetDto of(Integer setNum, WorkoutType type, String pace,
                                   double startPoint, double endPoint, String feedback) {
        return WorkoutSetDto.builder()
                .setNum(setNum)
                .type(type)
                .pace(pace)
                .startPoint(startPoint)
                .endPoint(endPoint)
                .feedback(feedback)
                .build();
    }

    @Override
    public String toString() {
        return "WorkoutSetDto{" +
                "setNum=" + setNum +
                ", pace='" + pace + '\'' +
                ", startPoint=" + startPoint +
                ", endPoint=" + endPoint +
                ", feedback='" + feedback + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

}
