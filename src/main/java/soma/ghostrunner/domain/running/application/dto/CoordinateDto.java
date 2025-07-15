package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor @NoArgsConstructor
public class CoordinateDto {

  private double lat;
  private double lng;

  @Override
  public String toString() {
    return String.format("{%f, %f}", lat, lng);
  }

}
