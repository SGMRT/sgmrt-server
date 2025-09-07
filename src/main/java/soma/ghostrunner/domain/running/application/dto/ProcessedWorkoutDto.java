package soma.ghostrunner.domain.running.application.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.List;

@Getter
public class ProcessedWorkoutDto {

    private RunningType runningType;
    private Double goalKm;
    private Integer expectedMinutes;
    List<ProcessedWorkoutSetDto> workoutSetDtos;

    @Builder(access = AccessLevel.PRIVATE)
    private ProcessedWorkoutDto(RunningType runningType, Double goalKm, List<ProcessedWorkoutSetDto> workoutSetDtos) {
        this.runningType = runningType;
        this.goalKm = goalKm;
        this.workoutSetDtos = workoutSetDtos;
    }

    public static ProcessedWorkoutDto of(RunningType runningType, Double goalKm, List<ProcessedWorkoutSetDto> workoutSetDtos) {
        return ProcessedWorkoutDto.builder()
                .runningType(runningType)
                .goalKm(goalKm)
                .workoutSetDtos(workoutSetDtos)
                .build();
    }

    public String toStringForPacemakerPrompt() {

        String setsJson = workoutSetDtos.stream()
                .map(dto -> String.format(
                        """
                                \t        {
                                \t            "setNum": %d,
                                \t            "pace_min/km": "%s",
                                \t            "start_km": %.1f,
                                \t            "end_km": %.1f,
                                \t            "feedback": %s\

                                \t        }""",
                        dto.getSetNum(),
                        dto.getPace(),
                        dto.getStartPoint(),
                        dto.getEndPoint(),
                        dto.getFeedback() == null ? "null" : "\"" + dto.getFeedback() + "\""
                ))
                .reduce((a, b) -> a + ",\n" + b)
                .orElse("");
        return String.format(
                "{\n\t    \"type\": \"%s\",\n\t    \"goal_km\": %.1f,\n\t    \"expected_minutes\": %s,\n\t    \"sets\": [\n%s\n\t    ]\n\t}",
                runningType,
                goalKm,
                expectedMinutes == null ? "null" : expectedMinutes,
                setsJson
        );
    }

}
