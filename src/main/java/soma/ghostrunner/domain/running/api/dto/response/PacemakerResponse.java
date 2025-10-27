package soma.ghostrunner.domain.running.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.domain.Pacemaker;

import java.util.List;

@Getter
@NoArgsConstructor
public class PacemakerResponse {

    private Long id;
    private Pacemaker.Norm norm;
    private String summary;
    private Double goalKm;
    private Integer expectedMinutes;
    private Double pace;
    private String initialMessage;
    private List<PacemakerSetResponse> sets;
    private PacemakerTimeTableResponse timeTable;

    @Builder
    public PacemakerResponse(Long id, Pacemaker.Norm norm, String summary, Double goalKm, Integer expectedMinutes,
                             String initialMessage, List<PacemakerSetResponse> sets, PacemakerTimeTableResponse timeTable) {
        this.id = id;
        this.norm = norm;
        this.summary = summary;
        this.goalKm = goalKm;
        this.expectedMinutes = expectedMinutes;
        this.pace = calculateMinPace(sets);
        this.initialMessage = initialMessage;
        this.sets = sets;
        this.timeTable = timeTable;
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
