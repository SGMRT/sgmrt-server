package soma.ghostrunner.domain.course.enums;

import java.util.Arrays;

public enum GhostSortType {
  ID("id"),
  AVERAGE_PACE("runningRecord.averagePace"),
  CADENCE("runningRecord.cadence"),
  DURATION("runningRecord.duration");

  private final String fieldName;

  GhostSortType(final String fieldName) {
    this.fieldName = fieldName;
  }

  public static String[] getAllFields() {
    return Arrays.stream(GhostSortType.values())
        .map(it -> it.fieldName)
        .toArray(String[]::new);
  }

  public static boolean isValidField(String fieldName) {
    return Arrays.stream(GhostSortType.values())
        .anyMatch(it -> it.fieldName.equals(fieldName));
  }

}
