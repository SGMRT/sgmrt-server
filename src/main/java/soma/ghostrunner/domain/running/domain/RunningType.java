package soma.ghostrunner.domain.running.domain;

public enum RunningType {
    E, M, T, I, R;

    public static RunningType convertToRunningType(String runningPurpose) {
        return switch (runningPurpose) {
            case "RECOVERY_JOGGING" -> E;
            case "STAMINA" -> I;
            case "SPEED" -> R;
            case "MARATHON" -> M;
            case "FREE" -> T;
            default -> throw new IllegalArgumentException("Unknown running purpose: " + runningPurpose);
        };
    }

}
