package soma.ghostrunner.domain.running.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.running.application.dto.response.DayRunInfo;

@Getter
@AllArgsConstructor @Builder
public class RunMonthlyStatusResponse {

    private int day;
    private boolean hasRun;

    public static RunMonthlyStatusResponse of(DayRunInfo dayRunInfo) {
        return RunMonthlyStatusResponse.builder()
                .day(dayRunInfo.getDay())
                .hasRun(dayRunInfo.getRunCount() > 0)
                .build();
    }

}
