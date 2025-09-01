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

    @Column(name = "run_start_point", nullable = false)
    private Double runStartPoint;

    @Column(name = "run_end_point", nullable = false)
    private Double runEndPoint;

    @Column(name = "run_pace_min/km", nullable = false)
    private Double runPaceMinKm;

    @Column(name = "recovery_start_point")
    private Double recoveryStartPoint;

    @Column(name = "recovery_end_point")
    private Double recoveryEndPoint;

    @Column(name = "recovery_pace_min/km")
    private Double recoveryPaceMinKm;

    @Column(name = "recovery_duration_sec")
    private Long recoveryDuration;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "pacemaker_id", nullable = false)
    private Pacemaker pacemaker;

    @Builder(access = AccessLevel.PRIVATE)
    public PacemakerSet(Integer setNum, String message, Double runStartPoint, Double runEndPoint,
                        Double runPaceMinKm, Double recoveryStartPoint, Double recoveryEndPoint,
                        Double recoveryPaceMinKm, Long recoveryDuration, Pacemaker pacemaker) {
        this.setNum = setNum;
        this.message = message;
        this.runStartPoint = runStartPoint;
        this.runEndPoint = runEndPoint;
        this.runPaceMinKm = runPaceMinKm;
        this.recoveryStartPoint = recoveryStartPoint;
        this.recoveryEndPoint = recoveryEndPoint;
        this.recoveryPaceMinKm = recoveryPaceMinKm;
        this.recoveryDuration = recoveryDuration;
        this.pacemaker = pacemaker;
    }

    public static PacemakerSet of(Integer setNum, String message, Double runStartPoint, Double runEndPoint,
                                  Double runPaceMinKm, Double recoveryStartPoint, Double recoveryEndPoint,
                                  Double recoveryPaceMinKm, Pacemaker pacemaker) {
        return PacemakerSet.builder()
                .setNum(setNum)
                .message(message)
                .runStartPoint(runStartPoint)
                .runEndPoint(runEndPoint)
                .runPaceMinKm(runPaceMinKm)
                .recoveryStartPoint(recoveryStartPoint)
                .recoveryEndPoint(recoveryEndPoint)
                .recoveryPaceMinKm(recoveryPaceMinKm)
                .recoveryDuration(0L)
                .pacemaker(pacemaker)
                .build();
    }

}
