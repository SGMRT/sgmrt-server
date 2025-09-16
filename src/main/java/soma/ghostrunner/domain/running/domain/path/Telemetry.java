package soma.ghostrunner.domain.running.domain.path;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Telemetry {

    private Long t;     // timestamp
    private Double y;       // 위도
    private Double x;       // 경도
    private Double d;       // 거리
    private Double p;       // 페이스
    private Double e;       // 고도
    private Integer c;      // 케이던스
    private Integer b;      // BPM
    private Boolean r;      // 러닝 유무

    public void calculateRelativeTimeStamp(Long startedAt) {
        this.t = this.t - startedAt;
    }

}
