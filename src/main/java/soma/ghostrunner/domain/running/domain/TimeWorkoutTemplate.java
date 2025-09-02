package soma.ghostrunner.domain.running.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TimeWorkoutTemplate {

    private String id;
    private List<TimeWorkoutSetTemplate> sets;

    @Getter
    @AllArgsConstructor
    public static class TimeWorkoutSetTemplate {
        private String runningType;
        private Integer runningDuration;
        private Integer recoveryDuration;
    }

}
