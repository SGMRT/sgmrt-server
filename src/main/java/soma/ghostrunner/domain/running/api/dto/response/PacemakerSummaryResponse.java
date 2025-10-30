package soma.ghostrunner.domain.running.api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.Pacemaker;

import java.util.List;

@Getter
@NoArgsConstructor
public class PacemakerSummaryResponse {

    private Long id;
    private String runningType;
    private Double pace;

    @Builder
    public PacemakerSummaryResponse(Long id, Pacemaker pacemaker, List<PacemakerSetResponse> sets) {
        this.id = id;
        this.runningType = pacemaker.getRunningType().toWorkoutWord();
        this.pace = calculateMinPace(sets);
    }

    private double calculateMinPace(List<PacemakerSetResponse> sets) {
        double minPace = Double.MAX_VALUE;
        for (PacemakerSetResponse setResponse : sets) {
            Double pace = setResponse.getPace();
            if (pace > 0.0 & pace < minPace) {
                minPace = pace;
            }
        }
        return minPace;
    }

}
