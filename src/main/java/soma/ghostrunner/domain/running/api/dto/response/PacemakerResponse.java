package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.Pacemaker.Norm;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PacemakerResponse {

    private Long id;
    private Norm norm;
    private String summary;
    private Double goalKm;
    private Integer expectedMinutes;
    private String initialMessage;
    private Long runningId;
    private List<PacemakerSetResponse> sets;

}
