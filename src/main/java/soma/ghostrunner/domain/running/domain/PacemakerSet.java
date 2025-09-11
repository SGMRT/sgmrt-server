package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.running.application.dto.WorkoutSetDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PacemakerSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_num", nullable = false)
    private Integer setNum;

    @Column(name = "message", nullable = false, columnDefinition = "LONGTEXT")
    private String message;

    @Column(name = "start_point", nullable = false)
    private Double startPoint;

    @Column(name = "end_point", nullable = false)
    private Double endPoint;

    @Column(name = "pace_min/km", nullable = false)
    private Double pace;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "pacemaker_id", nullable = false)
    private Pacemaker pacemaker;

    @Builder(access = AccessLevel.PRIVATE)
    public PacemakerSet(Integer setNum, String message, Double startPoint, Double endPoint,
                        Double pace, Pacemaker pacemaker) {
        this.setNum = setNum;
        this.message = message;
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.pace = pace;
        this.pacemaker = pacemaker;
    }

    public static PacemakerSet of(Integer setNum, String message, Double startPoint, Double endPoint,
                                  Double pace, Pacemaker pacemaker) {
        return PacemakerSet.builder()
                .setNum(setNum)
                .message(message)
                .startPoint(startPoint)
                .endPoint(endPoint)
                .pace(pace)
                .pacemaker(pacemaker)
                .build();
    }

    public static List<PacemakerSet> createPacemakerSets(List<WorkoutSetDto> dtos, Pacemaker pacemaker) {
        return dtos.stream()
                .map(dto -> PacemakerSet.builder()
                        .setNum(dto.getSetNum())
                        .pace(toMinuteSecond(dto.getPace()))
                        .startPoint(dto.getStartPoint())
                        .endPoint(dto.getEndPoint())
                        .message(dto.getMessage())
                        .pacemaker(pacemaker)
                        .build()).collect(Collectors.toList());
    }

    private static Double toMinuteSecond(String paceMinKm) {
        String[] times = paceMinKm.split(":");
        String timesStr = times[0] + "." + times[1];
        return Double.valueOf(timesStr);
    }

}
