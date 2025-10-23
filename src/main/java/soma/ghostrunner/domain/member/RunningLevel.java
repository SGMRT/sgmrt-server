package soma.ghostrunner.domain.member;

public enum RunningLevel {

    BEGINNER("입문자"),
    INTERMEDIATE("중급자"),
    ADVANCED("상급자");

    private final String displayName;

    RunningLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

}
