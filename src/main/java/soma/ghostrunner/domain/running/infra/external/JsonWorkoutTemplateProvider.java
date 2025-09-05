package soma.ghostrunner.domain.running.infra.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.domain.formula.WorkoutSet;
import soma.ghostrunner.domain.running.domain.formula.WorkoutTemplate;
import soma.ghostrunner.domain.running.domain.formula.WorkoutTemplateProvider;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class JsonWorkoutTemplateProvider implements WorkoutTemplateProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<WorkoutType, List<WorkoutTemplate>> workoutTemplatesMap;

    @Override
    public List<WorkoutTemplate> findWorkoutTemplates(WorkoutType type) {
        return workoutTemplatesMap.getOrDefault(type, List.of());
    }

    @PostConstruct
    public void loadWorkoutData() throws IOException {
        // Json 역직렬화
        List<JsonWorkoutDto> eWorkouts = loadFromJson("workouts/E_workouts.json");
        List<JsonWorkoutDto> iWorkouts = loadFromJson("workouts/I_workouts.json");
        List<JsonWorkoutDto> mWorkouts = loadFromJson("workouts/M_workouts.json");
        List<JsonWorkoutDto> rWorkouts = loadFromJson("workouts/R_workouts.json");
        List<JsonWorkoutDto> tWorkouts = loadFromJson("workouts/T_workouts.json");

        // R,I 러닝은 재셋팅
        rWorkouts.forEach(JsonWorkoutDto::cloneInRepetitions);
        iWorkouts.forEach(JsonWorkoutDto::cloneInRepetitions);

        // WorkoutTemplate 변환
        List<WorkoutTemplate> workoutTemplatesList = Stream.of(eWorkouts, iWorkouts, mWorkouts, rWorkouts, tWorkouts)
                .flatMap(List::stream)
                .map(JsonWorkoutDto::toWorkoutTemplate)
                .toList();
        this.workoutTemplatesMap = workoutTemplatesList.stream()
                .collect(Collectors.groupingBy(WorkoutTemplate::getWorkoutType));
    }

    private List<JsonWorkoutDto> loadFromJson(String filePath) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/" + filePath);
        InputStream inputStream = resource.getInputStream();
        return objectMapper.readValue(inputStream, new TypeReference<>() {});
    }

    @Data
    @NoArgsConstructor
    private static class JsonWorkoutDto {

        private String id;
        private List<JsonWorkoutSetDto> sets;
        private Integer repetitions;

        protected void cloneInRepetitions() {
            List<JsonWorkoutSetDto> base = new ArrayList<>(sets);
            List<JsonWorkoutSetDto> newSets = new ArrayList<>(base.size() * repetitions);

            for (int r = 0; r < repetitions; r++) {
                int setNum = r + 1;
                for (JsonWorkoutSetDto set : base) {
                    JsonWorkoutSetDto copy = JsonWorkoutSetDto.create(set);
                    copy.setSetNum(setNum);
                    newSets.add(copy);
                }
            }

            this.sets = newSets;
            this.repetitions = null;
        }

        protected WorkoutTemplate toWorkoutTemplate() {
            WorkoutType primaryType = WorkoutType.valueOf(this.id.split("-")[0]);

            List<WorkoutSet> sets = this.sets.stream()
                    .map(setDto -> {
                        Integer setNum = setDto.getSetNum();
                        WorkoutType type = WorkoutType.valueOf(setDto.getType());
                        if (setDto.getDuration() != null) {
                            return new WorkoutSet(setNum, type, WorkoutSet.UnitType.DURATION, setDto.getDuration());
                        } else if (setDto.getDistance() != null) {
                            return new WorkoutSet(setNum, type, WorkoutSet.UnitType.DISTANCE, setDto.getDistance());
                        }
                        return null;
                    })
                    .toList();

            return new WorkoutTemplate(this.id, primaryType, sets);
        }

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class JsonWorkoutSetDto {
        private Integer setNum;
        private String type;
        private Double duration;
        private Double distance;

        protected static JsonWorkoutSetDto create(JsonWorkoutSetDto src) {
            return new JsonWorkoutSetDto(src.setNum, src.getType(), src.getDuration(), src.getDistance());
        }
    }

}
