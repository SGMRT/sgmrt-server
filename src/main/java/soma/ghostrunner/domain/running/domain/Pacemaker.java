package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.Where;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.global.common.BaseTimeEntity;

import static soma.ghostrunner.domain.running.domain.Pacemaker.Status.COMPLETED;

@SQLDelete(sql = "UPDATE pacemaker SET deleted = true WHERE id=?")
@Where(clause = "deleted = false")
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

    @Column(name = "has_run_with")
    private Boolean hasRunWith;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "running_id")
    private Long runningId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "member_uuid")
    private String memberUuid;

    @Builder(access = AccessLevel.PRIVATE)
    public Pacemaker(Norm norm, String summary,
                     Double goalDistance, Integer expectedTime, String initialMessage,
                     Long runningId, Long courseId, String memberUuid) {
        this.norm = norm;
        this.summary = summary;
        this.goalDistance = goalDistance;
        this.expectedTime = expectedTime;
        this.initialMessage = initialMessage;
        this.runningId = runningId;
        this.courseId = courseId;
        this.status = Status.PROCEEDING;
        this.hasRunWith = false;
        this.memberUuid = memberUuid;
    }

    public static Pacemaker of(Norm norm, Double goalDistance, Long courseId, String memberUuid) {
        return Pacemaker.builder()
                .norm(norm)
                .goalDistance(goalDistance)
                .courseId(courseId)
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

    public void updateAfterRunning(Long runningId) {
        verifyAlreadyHasRunWith();
        this.runningId = runningId;
        this.hasRunWith = true;
    }

    private void verifyAlreadyHasRunWith() {
        if (hasRunWith) {
            throw new IllegalArgumentException("이미 함께 뛴 기록이 있는 페이스메이커입니다.");
        }
    }

}
