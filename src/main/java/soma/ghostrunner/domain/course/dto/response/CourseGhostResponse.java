package soma.ghostrunner.domain.course.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CourseGhostResponse {
  private Long runnerId;
  private String runnerProfileUrl;
  private String runnerNickname;

  private Long runningId;
  private String runningName;
  private Double averagePace;
  private Integer cadence;
  private Integer bpm;
  private Long duration;
  private Boolean isPublic;
  private LocalDateTime startedAt;
}
