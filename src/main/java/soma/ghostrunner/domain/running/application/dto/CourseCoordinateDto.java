package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCoordinateDto {
  private double lat;
  private double lng;

  @Override
  public String toString() {
    return String.format("{%f, %f}", lat, lng);
  }
}
