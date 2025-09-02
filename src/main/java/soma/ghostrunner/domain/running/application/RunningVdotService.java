package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.domain.VdotCalculator;
import soma.ghostrunner.domain.running.domain.VdotPaceProvider;
import soma.ghostrunner.domain.running.infra.dto.VdotPaceDto;

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

    public Double calculateExpectedPace(int vdot, String runningPurpose) {
        return vdotPaceProvider.getPaceByVdotAndRunningType(vdot, RunningType.convertToRunningType(runningPurpose));
    }

    public Map<RunningType, Double> getExpectedPaces(int vdot) {
        List<VdotPaceDto> vdotPaceDtos = vdotPaceProvider.getVdotPaceByVdot(vdot);
        return vdotPaceDtos.stream()
                .collect(Collectors.toMap(VdotPaceDto::type, VdotPaceDto::pacePerKm, (a, b) -> b));
    }

}
