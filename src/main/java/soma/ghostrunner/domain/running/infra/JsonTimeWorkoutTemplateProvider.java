package soma.ghostrunner.domain.running.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.TimeWorkoutTemplate;
import soma.ghostrunner.domain.running.domain.TimeWorkoutTemplateProvider;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static soma.ghostrunner.domain.running.domain.TimeWorkoutTemplate.*;

@Component
@RequiredArgsConstructor
public class JsonTimeWorkoutTemplateProvider implements TimeWorkoutTemplateProvider {

    private final ObjectMapper objectMapper;
    private final Map<RunningType, List<TimeWorkoutTemplate>> templateCache = new EnumMap<>(RunningType.class);

    @PostConstruct
    public void init() {
        for (RunningType type : RunningType.values()) {
            try {
                ClassPathResource resource = new ClassPathResource("static/" + type.name() + "_workouts.json");
                if (resource.exists()) {
                    List<TimeWorkoutTemplateDto> dtos = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
                    List<TimeWorkoutTemplate> templates = dtos.stream().map(this::toDomain).collect(Collectors.toList());
                    templateCache.put(type, templates);
                }
            } catch (IOException e) {
                throw new IllegalStateException(type.name() + " 훈련 템플릿 로딩 실패", e);
            }
        }
    }

    @Override
    public TimeWorkoutTemplate findBestFitWorkoutTemplate(RunningType runningType, double goalDistance,
                                                          Map<RunningType, Double> expectedPaces) {
        return null;
    }

    private TimeWorkoutTemplate toDomain(TimeWorkoutTemplateDto dto) {
        List<TimeWorkoutSetTemplate> domainSets = dto.getSets().stream()
                .map(setDto -> new TimeWorkoutSetTemplate(
                        setDto.getRunningType(),
                        setDto.getRunningDuration(),
                        setDto.getRecoveryDuration()
                ))
                .collect(Collectors.toList());
        return new TimeWorkoutTemplate(dto.getId(), domainSets);
    }

    @Getter
    @AllArgsConstructor
    private static class TimeWorkoutTemplateDto {
        private String id;
        private List<TimeWorkoutSetTemplateDto> sets;
    }

    @Getter
    @AllArgsConstructor
    private static class TimeWorkoutSetTemplateDto {
        private String runningType;
        private Integer runningDuration;
        private Integer recoveryDuration;
    }

}
