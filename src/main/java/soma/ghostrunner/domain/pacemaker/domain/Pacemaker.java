package soma.ghostrunner.domain.pacemaker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.Duration;
import java.time.LocalDateTime;


@SQLDelete(sql = "UPDATE pacemaker SET deleted = true WHERE id=?")
@Where(clause = "deleted = false")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pacemaker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "running_type")
    @Enumerated(EnumType.STRING)
    private RunningType runningType;

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

    @Column(name = "condition_level")
    private Integer condition;

    @Column(name = "temperature")
    private Integer temperature;

    @Builder(access = AccessLevel.PRIVATE)
    public Pacemaker(RunningType runningType, Norm norm, String summary,
                     Double goalDistance, Integer expectedTime, String initialMessage,
                     Long runningId, Long courseId, String memberUuid, Status status,
                     Integer condition, Integer temperature) {
        this.runningType = runningType;
        this.norm = norm;
        this.summary = summary;
        this.goalDistance = goalDistance;
        this.expectedTime = expectedTime;
        this.initialMessage = initialMessage;
        this.runningId = runningId;
        this.courseId = courseId;
        this.status = status != null ? status : Status.INIT;
        this.hasRunWith = false;
        this.memberUuid = memberUuid;
        this.condition = condition;
        this.temperature = temperature;
    }

    public static Pacemaker of(Norm norm, Double goalDistance, Long courseId, RunningType runningType, String memberUuid) {
        return Pacemaker.builder()
                .norm(norm)
                .goalDistance(goalDistance)
                .courseId(courseId)
                .runningType(runningType)
                .memberUuid(memberUuid)
                .status(Status.INIT)
                .build();
    }

    public static Pacemaker createWithRuleBase(Norm norm, Double goalDistance, Integer expectedTime,
                                                Long courseId, RunningType runningType, String memberUuid,
                                                Integer condition, Integer temperature) {
        return Pacemaker.builder()
                .norm(norm)
                .goalDistance(goalDistance)
                .expectedTime(expectedTime)
                .courseId(courseId)
                .runningType(runningType)
                .memberUuid(memberUuid)
                .condition(condition)
                .temperature(temperature)
                .status(Status.INIT)
                .build();
    }

    public enum Norm {
        DISTANCE, TIME
    }

    public enum Status {
        INIT {
            @Override
            public boolean canTransitionTo(Status next) {
                return next == PROCEEDING || next == FAILED;
            }
        },
        PROCEEDING {
            @Override
            public boolean canTransitionTo(Status next) {
                return next == COMPLETED || next == FAILED;
            }
        },
        COMPLETED {
            @Override
            public boolean canTransitionTo(Status next) {
                return false;
            }
        },
        FAILED {
            @Override
            public boolean canTransitionTo(Status next) {
                return false;
            }
        };

        public abstract boolean canTransitionTo(Status next);
    }

    public void proceed() {
        validateStatusTransition(Status.PROCEEDING);
        this.status = Status.PROCEEDING;
    }

    public void complete(String summary, Double goalKm, Integer expectedMinutes, String initialMessage) {
        validateStatusTransition(Status.COMPLETED);
        this.summary = summary;
        this.goalDistance = goalKm;
        this.expectedTime = expectedMinutes;
        this.initialMessage = initialMessage;
        this.status = Status.COMPLETED;
    }

    public void fallback() {
        validateStatusTransition(Status.FAILED);
        this.status = Status.FAILED;
    }

    /**
     * 지연 판정 — 미완료(INIT/PROCEEDING)인 채 threshold를 넘겼으면 FALLBACK(FAILED)으로 전환한다.
     *
     * @return 전환이 일어났으면 true — 고아 레코드였다는 뜻이므로 호출자가 알림을 남긴다
     */
    public boolean fallbackIfStaleOver(Duration threshold) {
        boolean stale = isNotCompleted()
                && getCreatedAt().isBefore(LocalDateTime.now().minus(threshold));
        if (stale) {
            fallback();
        }
        return stale;
    }

    private void validateStatusTransition(Status next) {
        if (!this.status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", this.status, next));
        }
    }

    public void verifyMember(String memberUuid) {
        if (!this.memberUuid.equals(memberUuid)) {
            throw new AccessDeniedException("접근할 수 없는 러닝 데이터입니다.");
        }
    }

    public boolean isNotCompleted() {
        return !status.equals(Status.COMPLETED) && !status.equals(Status.FAILED);
    }

    public boolean isCompleted() {
        return status.equals(Status.COMPLETED) || status.equals(Status.FAILED);
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
