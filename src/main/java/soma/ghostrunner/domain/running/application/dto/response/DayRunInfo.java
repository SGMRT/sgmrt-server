package soma.ghostrunner.domain.running.application.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class DayRunInfo {

    private Integer year;
    private Integer month;
    private Integer day;
    private Integer runCount;

    @QueryProjection
    public DayRunInfo(Integer year, Integer month, Integer day, Integer runCount) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.runCount = runCount;
    }

}
