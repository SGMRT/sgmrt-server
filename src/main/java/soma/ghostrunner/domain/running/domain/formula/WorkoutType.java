package soma.ghostrunner.domain.running.domain.formula;

public enum WorkoutType {

    E("이지"), M("마라톤"), T("Threshold"), I("인터벌"), R("Repetition"), X("휴식");

    private final String type;

    WorkoutType(String type) {
        this.type = type;
    }

}
