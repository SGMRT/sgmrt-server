package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.global.common.BaseTimeEntity;

import static soma.ghostrunner.domain.running.domain.Pacemaker.Status.COMPLETED;

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

    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Column(name = "goal_km", nullable = false)
    private Double goalDistance;

    @Column(name = "expected_time_min")
    private Integer expectedTime;

    @Column(name = "initial_message", columnDefinition = "LONGTEXT")
    private String initialMessage;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "running_id")
    private Long runningId;

    @Column(name = "member_uuid")
    private String memberUuid;

    @Builder(access = AccessLevel.PRIVATE)
    public Pacemaker(Norm norm, String summary,
                     Double goalDistance, Integer expectedTime, String initialMessage,
                     Long runningId, String memberUuid) {
        this.norm = norm;
        this.summary = summary;
        this.goalDistance = goalDistance;
        this.expectedTime = expectedTime;
        this.initialMessage = initialMessage;
        this.runningId = runningId;
        this.status = Status.PROCEEDING;
        this.memberUuid = memberUuid;
    }

    public static Pacemaker of(Norm norm, Double goalDistance, String memberUuid) {
        return Pacemaker.builder()
                .norm(norm)
                .goalDistance(goalDistance)
                .memberUuid(memberUuid)
                .build();
    }

    public void updateSucceedPacemaker(String summary, Double goalKm, Integer expectedMinutes, String initialMessage) {
        this.summary = summary;
        this.goalDistance = goalKm;
        this.expectedTime = expectedMinutes;
        this.initialMessage = initialMessage;
        this.status = COMPLETED;
    }

    public enum Norm {
        DISTANCE, TIME
    }

    public enum Status {
        PROCEEDING, COMPLETED, FAILED
    }

    public void updateStatus(Status status) {
        this.status = status;
    }

    public void verifyMember(String memberUuid) {
        if (!this.memberUuid.equals(memberUuid)) {
            throw new AccessDeniedException("접근할 수 없는 러닝 데이터입니다.");
        }
    }

    public boolean isNotCompleted() {
        return !status.equals(COMPLETED);
    }

}
