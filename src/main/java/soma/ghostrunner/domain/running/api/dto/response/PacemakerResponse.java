package soma.ghostrunner.domain.running.api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PacemakerResponse {

    private Long id;
    private String runningType;
    private String norm;
    private String summary;
    private Double goalKm;
    private Integer expectedMinutes;
    private Double pace;
    private String initialMessage;
    private List<PacemakerSetResponse> sets;
    private PacemakerTimeTableResponse timeTable;
    private String runningTip;

    @Builder
    public PacemakerResponse(Long id, String runningType, String norm, String summary,
                             Double goalKm, Integer expectedMinutes, String initialMessage,
                             List<PacemakerSetResponse> sets, PacemakerTimeTableResponse timeTable, String runningTip) {
        this.id = id;
        this.runningType = runningType;
        this.norm = norm;
        this.summary = summary;
        this.goalKm = goalKm;
        this.expectedMinutes = expectedMinutes;
        this.pace = calculateMinPace(sets);
        this.initialMessage = initialMessage;
        this.sets = sets;
        this.timeTable = timeTable;
        this.runningTip = runningTip;
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
