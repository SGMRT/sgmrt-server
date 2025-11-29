package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.formula.VdotCalculator;
import soma.ghostrunner.domain.running.domain.formula.VdotPaceProvider;
import soma.ghostrunner.domain.running.domain.formula.VdotPace;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunningVdotService {

    private final VdotCalculator vdotCalculator;
    private final VdotPaceProvider vdotPaceProvider;

    public int calculateVdot(Double averagePace) {
        double oneMilePace = Running.calculateOneMilePace(averagePace);
        return vdotCalculator.calculateFromPace(oneMilePace);
    }

    public int calculateVdotFromRunningLevel(String level) {
        double oneMilePace = Running.calculatePaceFromRunningLevel(level);
        return vdotCalculator.calculateFromPace(oneMilePace);
    }

    public Map<RunningType, Double> getExpectedPacesByVdot(int vdot) {
        List<VdotPace> vdotPaces = vdotPaceProvider.getVdotPaceByVdot(vdot);
        return vdotPaces.stream()
                .collect(Collectors.toMap(VdotPace::type, VdotPace::pacePerKm, (a, b) -> b));
    }

}
