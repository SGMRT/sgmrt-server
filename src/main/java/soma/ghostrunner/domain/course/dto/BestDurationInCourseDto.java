package soma.ghostrunner.domain.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class BestDurationInCourseDto {

    private Long courseId;
    private Long memberId;
    private Long duration;

    public static List<BestDurationInCourseDto> toList(List<BestDurationProjection> projections) {
        return projections.stream()
                .map(p -> new BestDurationInCourseDto(
                        p.getCourseId(),
                        p.getMemberId(),
                        p.getBestDurationSec()
                ))
                .collect(Collectors.toList());
    }

}
