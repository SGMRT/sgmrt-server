package soma.ghostrunner.domain.course.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CourseRunStatisticsDto {
  private Double avgCompletionTime;
  private Double avgFinisherPace;
  private Double avgFinisherCadence;
  private Double lowestFinisherPace;

  @QueryProjection
  public CourseRunStatisticsDto(
      Double avgCompletionTime,
      Double avgFinisherPace,
      Double avgFinisherCadence,
      Double lowestFinisherPace) {
    this.avgCompletionTime = avgCompletionTime;
    this.avgFinisherPace = avgFinisherPace;
    this.avgFinisherCadence = avgFinisherCadence;
    this.lowestFinisherPace = lowestFinisherPace;
  }
}
