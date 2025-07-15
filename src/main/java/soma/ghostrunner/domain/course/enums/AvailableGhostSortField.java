package soma.ghostrunner.domain.course.enums;

import java.util.Arrays;

public enum AvailableGhostSortField {
  ID("id"),
  AVERAGE_PACE("runningRecord.averagePace"),
  CADENCE("runningRecord.cadence"),
  DURATION("runningRecord.duration");

  private final String fieldName;

  AvailableGhostSortField(final String fieldName) {
    this.fieldName = fieldName;
  }

  public static String[] getAllFields() {
    return Arrays.stream(AvailableGhostSortField.values())
        .map(it -> it.fieldName)
        .toArray(String[]::new);
  }

  public static boolean isValidField(String fieldName) {
    return Arrays.stream(AvailableGhostSortField.values())
        .anyMatch(it -> it.fieldName.equals(fieldName));
  }

}
