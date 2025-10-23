package soma.ghostrunner.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.course.domain.CourseReadModel;

@Getter
@AllArgsConstructor
public class CourseMapResponse3 {
    private CourseReadModel courseInfo;
    private boolean hasMyRecord;
}
