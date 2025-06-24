package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateCourseAndRunResponse {

    private Long runningId;

    private Long courseId;

    public static CreateCourseAndRunResponse of(Long runningId, Long courseId) {
        return new CreateCourseAndRunResponse(runningId, courseId);
    }
}
