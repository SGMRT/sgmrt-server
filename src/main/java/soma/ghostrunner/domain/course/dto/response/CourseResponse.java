package soma.ghostrunner.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseResponse {
    private Long id;
    private String name;
    private Double startLat;
    private Double startLng;
    private Integer distance;
    private Integer elevationGain;
    private Integer elevationLoss;

    public static CourseResponse of(
            Long id,
            String name,
            Double startLat,
            Double startLng,
            Integer distance,
            Integer elevationGain,
            Integer elevationLoss) {
        return new CourseResponse(id, name, startLat, startLng, distance, elevationGain, elevationLoss);
    }
}
