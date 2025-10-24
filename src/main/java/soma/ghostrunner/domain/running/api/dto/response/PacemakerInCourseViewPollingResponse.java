package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class PacemakerInCourseViewPollingResponse {

    private String processingStatus;
    private PacemakerResponse pacemakerResponse;

}
