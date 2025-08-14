package soma.ghostrunner.domain.course.enums;

public enum CourseSortType {
    DISTANCE("거리 순"),
    POPULARITY("인기 순");

    private final String description;
    CourseSortType(String description) {
        this.description = description;
    }
}
