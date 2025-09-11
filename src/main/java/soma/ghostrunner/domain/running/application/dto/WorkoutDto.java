package soma.ghostrunner.domain.running.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.running.domain.RunningType;

import java.util.List;

@Getter
public class WorkoutDto {

    private RunningType type;

    @JsonProperty("goal_km")
    private Double goalKm;

    List<WorkoutSetDto> sets;

    @JsonProperty("expected_minutes")
    private Integer expectedMinutes;

    private String summary;

    @JsonProperty("initial_message")
    private String initialMessage;

    @Builder(access = AccessLevel.PRIVATE)
    private WorkoutDto(RunningType type, Double goalKm,
                       List<WorkoutSetDto> sets, Integer expectedMinutes) {
        this.type = type;
        this.goalKm = goalKm;
        this.sets = sets;
        this.expectedMinutes = expectedMinutes;
    }

    public static WorkoutDto of(RunningType type, Double goalKm, List<WorkoutSetDto> sets) {
        return WorkoutDto.builder()
                .type(type)
                .goalKm(goalKm)
                .sets(sets)
                .build();
    }

    public static WorkoutDto of(RunningType type, Double goalKm,
                                List<WorkoutSetDto> sets, Integer expectedMinutes) {
        return WorkoutDto.builder()
                .type(type)
                .goalKm(goalKm)
                .sets(sets)
                .expectedMinutes(expectedMinutes)
                .build();
    }

    public static WorkoutDto fromProcessedWorkoutDto(String processedStrWorkoutDto) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            return mapper.readValue(processedStrWorkoutDto, WorkoutDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON into ProcessedWorkoutDto", e);
        }
    }

    public static WorkoutDto fromVoiceGuidanceGeneratedWorkoutDto(String voiceGuidanceGeneratedStrWorkoutDto) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();

            JsonNode root = mapper.readTree(voiceGuidanceGeneratedStrWorkoutDto);
            JsonNode workoutNode = root.get("workout");
            if (workoutNode == null || workoutNode.isMissingNode()) {
                throw new RuntimeException("JSON does not contain 'workout' field");
            }

            return mapper.treeToValue(workoutNode, WorkoutDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON into WorkoutDto", e);
        }
    }

    public String toStringForWorkoutImprovementPrompt() {

        String setsJson = sets.stream()
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
                type,
                goalKm,
                expectedMinutes == null ? "null" : expectedMinutes,
                setsJson
        );
    }

    public String toStringForVoiceGuidancePrompt() {

        String setsJson = sets.stream()
                .map(dto -> String.format(
                        """
                                \t        {
                                \t            "setNum": %d,
                                \t            "pace_min/km": "%s",
                                \t            "start_km": %.1f,
                                \t            "end_km": %.1f,
                                \t            "feedback": %s,
                                \t            "message": %s\

                                \t        }""",
                        dto.getSetNum(),
                        dto.getPace(),
                        dto.getStartPoint(),
                        dto.getEndPoint(),
                        dto.getFeedback() == null ? "null" : "\"" + dto.getFeedback() + "\"",
                        dto.getMessage() == null ? "null" : "\"" + dto.getFeedback() + "\""
                ))
                .reduce((a, b) -> a + ",\n" + b)
                .orElse("");
        return String.format(
                "{\n\t    " +
                        "\"type\": \"%s\",\n\t    \"goal_km\": %.1f,\n\t    \"expected_minutes\": %s," +
                        "\n\t    \"summary\": %s,\n\t    \"initial_message\": %s," +
                        "\n\t    \"sets\": [\n%s\n\t    ]\n\t}",
                type,
                goalKm,
                expectedMinutes == null ? "null" : expectedMinutes,
                summary == null ? "null" : summary,
                initialMessage == null ? "null" : initialMessage,
                setsJson
        );
    }

    @Override
    public String toString() {
        return "WorkoutDto{" +
                "type='" + type + '\'' +
                ", goalKm=" + goalKm +
                ", expectedMinutes=" + expectedMinutes +
                ", summary='" + summary + '\'' +
                ", initialMessage='" + initialMessage + '\'' +
                ", sets=" + sets +
                '}';
    }

}
