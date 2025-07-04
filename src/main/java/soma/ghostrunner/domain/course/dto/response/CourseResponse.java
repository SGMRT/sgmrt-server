package soma.ghostrunner.domain.course.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseResponse {
    private Long id;
    private String name;
    private Double startLat;
    private Double startLng;
    private List<CourseCoordinateDto> pathData;
    private Integer distance;
    private Integer elevationGain;
    private Integer elevationLoss;

    public static CourseResponse of(
            Long id,
            String name,
            Double startLat,
            Double startLng,
            List<CourseCoordinateDto> pathData,
            Integer distance,
            Integer elevationGain,
            Integer elevationLoss) {
        return new CourseResponse(id, name, startLat, startLng, pathData, distance, elevationGain, elevationLoss);
    }
}
