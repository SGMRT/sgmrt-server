package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
@AllArgsConstructor
public class PacemakerTimeTableResponse {

    private Integer warmUpMinutes;
    private Integer maintenanceMinutes;
    private Integer coolDownMinutes;

    public PacemakerTimeTableResponse(List<PacemakerSetResponse> sets, Integer expectedMinutes) {
        this.warmUpMinutes = calculateTimeTableMinutes(sets.get(0));
        this.coolDownMinutes = calculateTimeTableMinutes(sets.get(sets.size() - 1));
        this.maintenanceMinutes = expectedMinutes - coolDownMinutes - warmUpMinutes;
    }

    private Integer calculateTimeTableMinutes(PacemakerSetResponse setResponse) {
        Double distKm = setResponse.getEndPoint() - setResponse.getStartPoint();
        return (int) (distKm * setResponse.getPace());
    }

}
