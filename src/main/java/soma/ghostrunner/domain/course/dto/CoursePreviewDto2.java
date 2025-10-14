package soma.ghostrunner.domain.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@AllArgsConstructor
public class CoursePreviewDto2 {

    private Long courseId;
    private List<Long> top4RunnerIds;
    private boolean hasMyRecord;
    private Integer runnersCount;

    public static CoursePreviewDto2 of(List<BestDurationInCourseDto> bestDurationInCourseDtos, Long viewerId) {

        bestDurationInCourseDtos.sort(Comparator.comparing(BestDurationInCourseDto::getDuration));

        List<Long> top4RunnerIds = new ArrayList<>();
        if (bestDurationInCourseDtos.size() > 4) {
            for (int i = 0; i < 4; i++) {
                top4RunnerIds.add(bestDurationInCourseDtos.get(i).getMemberId());
            }
        } else {
            for (int i = 0; i < bestDurationInCourseDtos.size(); i++) {
                top4RunnerIds.add(bestDurationInCourseDtos.get(i).getMemberId());
            }
        }

        boolean hasMyRecord = false;
        for (BestDurationInCourseDto dto : bestDurationInCourseDtos) {
            if (dto.getMemberId().equals(viewerId)) {
                hasMyRecord = true;
            }
        }

        return new CoursePreviewDto2(
                bestDurationInCourseDtos.get(0).getCourseId(),
                top4RunnerIds,
                hasMyRecord,
                bestDurationInCourseDtos.size()
        );
    }

}
