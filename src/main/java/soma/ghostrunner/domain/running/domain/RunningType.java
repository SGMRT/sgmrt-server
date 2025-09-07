package soma.ghostrunner.domain.running.domain;

import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

public enum RunningType {
    E, M, T, I, R;

    public static RunningType toRunningType(String runningPurpose) {
        return switch (runningPurpose) {
            case "RECOVERY_JOGGING" -> E;
            case "STAMINA" -> I;
            case "SPEED" -> R;
            case "MARATHON" -> M;
            case "FREE" -> T;
            default -> throw new IllegalArgumentException("Unknown running purpose: " + runningPurpose);
        };
    }

    public static RunningType toRunningType(WorkoutType workoutType) {
        return RunningType.valueOf(workoutType.name());
    }

}
