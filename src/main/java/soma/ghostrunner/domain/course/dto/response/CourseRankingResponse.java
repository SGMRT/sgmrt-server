package soma.ghostrunner.domain.course.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class CourseRankingResponse {
  private Integer rank;
  private Long runningId;
  private String runningName;
  private Long duration;
  private Integer bpm;
  private Double averagePace;

  @QueryProjection
  public CourseRankingResponse(Integer rank, Long runningId, String runningName, Long duration, Integer bpm, Double averagePace) {
    this.rank = rank;
    this.runningId = runningId;
    this.runningName = runningName;
    this.duration = duration;
    this.bpm = bpm;
    this.averagePace = averagePace;
  }
}
