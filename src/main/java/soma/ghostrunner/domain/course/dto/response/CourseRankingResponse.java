package soma.ghostrunner.domain.course.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CourseRankingResponse {
  private Integer rank;
  private String runnerUuid;
  private String runnerProfileUrl;
  private String runnerNickname;

  private Long runningId;
  private String runningName;
  private Long duration;
  private Integer bpm;
  private Integer cadence;
  private Double averagePace;
  private LocalDateTime startedAt;

  @QueryProjection
  public CourseRankingResponse(Integer rank, String runnerUuid, String runnerProfileUrl, String runnerNickname,
                               Long runningId, String runningName, Long duration, Integer bpm,
                               Integer cadence, Double averagePace, LocalDateTime startedAt) {
    this.rank = rank;
    this.runnerUuid = runnerUuid;
    this.runnerProfileUrl = runnerProfileUrl;
    this.runnerNickname = runnerNickname;
    this.runningId = runningId;
    this.runningName = runningName;
    this.duration = duration;
    this.bpm = bpm;
    this.cadence = cadence;
    this.averagePace = averagePace;
    this.startedAt = startedAt;
  }
}
