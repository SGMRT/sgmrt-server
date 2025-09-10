package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.WorkoutSetDto;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.WorkoutSet;
import soma.ghostrunner.domain.running.domain.formula.Workout;
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

    public WorkoutDto generateWorkouts(double targetDistance, RunningType type, Map<RunningType, Double> paces) {
        List<Workout> workouts = workoutTemplateProvider.findWorkoutTemplates(WorkoutType.valueOf(type.name()));

        Workout bestWorkout = workouts.stream()
                .min(Comparator.comparingDouble(t -> Math.abs(t.calculateTotalDistance(paces) - targetDistance)))
                .orElseThrow(() -> new RuntimeException("최적 템플릿을 찾지 못했습니다."));

        double totalDistanceOfWorkout = bestWorkout.calculateTotalDistance(paces);
        double scaleFactor = targetDistance / totalDistanceOfWorkout;
        List<WorkoutSetDto> workoutSetDtos = scaleAndProcessWorkout(bestWorkout, scaleFactor, paces);
        return WorkoutDto.of(type, targetDistance, workoutSetDtos);
    }

    private List<WorkoutSetDto> scaleAndProcessWorkout(Workout template, double scaleFactor,
                                                       Map<RunningType, Double> paces) {

        double currentDistance = 0;

        List<WorkoutSet> sets = template.getSets();
        List<WorkoutSetDto> dtos = new ArrayList<>(sets.size());

        for (int i = 0; i < sets.size(); i++) {
            WorkoutSet set = sets.get(i);

            double originalDistance = set.convertToDistance(paces);
            double scaledDistance = originalDistance * scaleFactor;

            WorkoutSetDto dto = WorkoutSetDto.of(
                    set.getSetNum(),
                    set.getType(),
                    set.getType() == WorkoutType.X ? "0:00" : toMinuteSecond(paces.get(RunningType.toRunningType(set.getType()))),
                    currentDistance,
                    currentDistance + scaledDistance
            );

            dtos.add(dto);
            currentDistance += scaledDistance;
        }

        for (int i = 1; i < dtos.size()+1; i++) {
            WorkoutSetDto dto = dtos.get(i-1);
            dto.setSetNum(i);
        }

        return dtos;
    }

    private String toMinuteSecond(double paceMinKm) {
        int minutes = (int) paceMinKm;
        int seconds = (int) Math.round((paceMinKm - minutes) * 100);
        return String.format("%d:%02d", minutes, seconds);
    }

}
