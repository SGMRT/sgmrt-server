package soma.ghostrunner.domain.course.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserPaceStatsDto {
    private String memberUuid;
    private Double lowestPace;
    private Double avgPace;
    private Double highestPace;
                
    @QueryProjection
    public UserPaceStatsDto (String memberUuid,  Double lowestPace, Double avgPace, Double highestPace) {
        this.memberUuid = memberUuid;
        this.lowestPace = lowestPace;
        this.avgPace = avgPace;
        this.highestPace = highestPace;
    }
}
