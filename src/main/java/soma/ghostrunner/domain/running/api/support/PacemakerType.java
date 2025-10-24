package soma.ghostrunner.domain.running.api.support;

public enum PacemakerType {

    RECOVERY_JOGGING("회복 러닝"),
    STAMINA("체력 증진"),
    SPEED("속도 높이기"),
    MARATHON("마라톤"),
    FREE("기분 대로 달리기");

    private final String displayName;

    PacemakerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
