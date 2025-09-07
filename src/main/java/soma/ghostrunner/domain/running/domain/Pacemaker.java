package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.global.common.BaseTimeEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pacemaker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "norm")
    @Enumerated(EnumType.STRING)
    private Norm norm;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "goal_km", nullable = false)
    private Double goalDistance;

    @Column(name = "expected_time_min", nullable = false)
    private Integer expectedTime;

    @Column(name = "initial_message", nullable = false)
    private String initialMessage;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "running_id", nullable = false)
    private Long runningId;

    @Builder(access = AccessLevel.PRIVATE)
    public Pacemaker(Norm norm, String summary, Double goalDistance,
                     Integer expectedTime, String initialMessage, Long runningId) {
        this.norm = norm;
        this.summary = summary;
        this.goalDistance = goalDistance;
        this.expectedTime = expectedTime;
        this.initialMessage = initialMessage;
        this.runningId = runningId;
        this.status = Status.PROCEEDING;
    }

    public static Pacemaker of(Norm norm, Double goalDistance, Long runningId) {
        return Pacemaker.builder()
                .norm(norm)
                .goalDistance(goalDistance)
                .runningId(runningId)
                .build();
    }

    public enum Norm {
        DISTANCE, TIME
    }

    public enum Status {
        PROCEEDING, COMPLETED, FAILED
    }

}
