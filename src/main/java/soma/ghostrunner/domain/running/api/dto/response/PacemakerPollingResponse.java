package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.Pacemaker;

import java.util.List;

@Getter
@AllArgsConstructor @Builder
@NoArgsConstructor
public class PacemakerPollingResponse {

    private String processingStatus;
    private PacemakerResponse pacemakerResponse;

}
