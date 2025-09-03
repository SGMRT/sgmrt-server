package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PacemakerSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_num", nullable = false)
    private Integer setNum;

    @Column(name = "message", nullable = false)
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

}
