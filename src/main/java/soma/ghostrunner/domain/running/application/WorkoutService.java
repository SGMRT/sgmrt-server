package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.running.application.dto.ProcessedWorkoutDto;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.WorkoutSet;
import soma.ghostrunner.domain.running.domain.formula.WorkoutTemplate;
import soma.ghostrunner.domain.running.domain.formula.WorkoutTemplateProvider;
import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutTemplateProvider workoutTemplateProvider;

    public List<ProcessedWorkoutDto> generatePlan(double targetDistance, RunningType type, Map<RunningType, Double> paces) {
        List<WorkoutTemplate> templates = workoutTemplateProvider.findWorkoutTemplates(WorkoutType.valueOf(type.name()));

        WorkoutTemplate bestTemplate = templates.stream()
                .min(Comparator.comparingDouble(t -> Math.abs(t.calculateTotalDistance(paces) - targetDistance)))
                .orElseThrow(() -> new RuntimeException("최적 템플릿을 찾지 못했습니다."));

        double totalDistanceOfTemplate = bestTemplate.calculateTotalDistance(paces);
        double scaleFactor = targetDistance / totalDistanceOfTemplate;
        return scaleAndProcessTemplate(bestTemplate, scaleFactor, paces);
    }

    private List<ProcessedWorkoutDto> scaleAndProcessTemplate(WorkoutTemplate template,
                                                              double scaleFactor, Map<RunningType, Double> paces) {

        double currentDistance = 0;

        List<WorkoutSet> sets = template.getSets();
        List<ProcessedWorkoutDto> dtos = new ArrayList<>(sets.size());

        for (int i = 0; i < sets.size(); i++) {
            WorkoutSet set = sets.get(i);

            double originalDistance = set.convertToDistance(paces);
            double scaledDistance = originalDistance * scaleFactor;

            ProcessedWorkoutDto dto = new ProcessedWorkoutDto(
                    set.getSetNum(),
                    set.getType(),
                    currentDistance,
                    currentDistance + scaledDistance
            );

            dtos.add(dto);
            currentDistance += scaledDistance;
        }

        return dtos;
    }

}
