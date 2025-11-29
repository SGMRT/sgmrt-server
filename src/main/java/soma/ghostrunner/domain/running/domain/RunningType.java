package soma.ghostrunner.domain.running.domain;

import soma.ghostrunner.domain.running.domain.formula.WorkoutType;

public enum RunningType {

    E("이지"), M("마라톤"), T("Threshold"), I("인터벌"), R("Repetition");

    private final String type;

    RunningType(String type) {
        this.type = type;
    }

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

    public String toWorkoutWord() {
        return switch (this) {
            case E -> "RECOVERY_JOGGING";
            case I -> "STAMINA";
            case R -> "SPEED";
            case M -> "MARATHON";
            case T -> "FREE";
        };
    }

    public static RunningType toRunningType(WorkoutType workoutType) {
        return RunningType.valueOf(workoutType.name());
    }

}
