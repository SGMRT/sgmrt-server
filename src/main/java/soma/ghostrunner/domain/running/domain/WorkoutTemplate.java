package soma.ghostrunner.domain.running.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class WorkoutTemplate {

    private String id;
    private List<WorkoutSetTemplate> sets;

    @Getter
    @AllArgsConstructor
    public static class WorkoutSetTemplate {
        private String runningType;
        private Integer runningDuration;
        private Integer recoveryDuration;
    }

}
