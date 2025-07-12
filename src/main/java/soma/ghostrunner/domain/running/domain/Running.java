package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.common.error.ErrorCode;

@Entity
@Table(name = "running_record")
@SoftDelete
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Running extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "running_name", nullable = false)
    private String runningName;

    @Column(name = "running_mode")
    @Enumerated(EnumType.STRING)
    private RunningMode runningMode;

    @Column(name = "ghost_running_id")
    private Long ghostRunningId;

    @Embedded
    private RunningRecord runningRecord;

    @Column(name = "started_at_ms", nullable = false)
    private Long startedAt;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "has_paused", nullable = false)
    private boolean hasPaused;

    @Embedded
    private RunningSummary runningSummary;

    @Column(name = "telemetry_url", nullable = false, length = 2048)
    private String telemetryUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Builder
    private Running(String runningName, RunningMode runningMode, Long ghostRunningId, RunningRecord runningRecord, Long startedAt,
                    boolean isPublic, boolean hasPaused, String telemetryUrl, Member member, Course course) {
        this.runningName = runningName;
        this.runningMode = runningMode;
        this.ghostRunningId = ghostRunningId;
        this.runningRecord = runningRecord;
        this.startedAt = startedAt;
        this.isPublic = isPublic;
        this.hasPaused = hasPaused;
        this.telemetryUrl = telemetryUrl;
        this.member = member;
        this.course = course;
    }

    public static Running of(String runningName, RunningMode runningMode, Long ghostRunningId, RunningRecord runningRecord, Long startedAt,
                             boolean isPublic, boolean hasPaused, String telemetryUrl, Member member, Course course) {
        return Running.builder()
                .runningName(runningName)
                .runningMode(runningMode)
                .ghostRunningId(ghostRunningId)
                .runningRecord(runningRecord)
                .startedAt(startedAt)
                .isPublic(isPublic)
                .hasPaused(hasPaused)
                .telemetryUrl(telemetryUrl)
                .member(member)
                .course(course)
                .build();
    }

    public void updateName(String name) {
        this.runningName = name;
    }

    public void updatePublicStatus() {
        if (this.isPublic) {
            makePrivate();
        } else {
            makePublic();
        }
    }

    private void makePublic() {
        validateCanBePublic();
        this.isPublic = true;
    }

    private void makePrivate() {
        this.isPublic = false;
    }

    private void validateCanBePublic() {
        if (this.hasPaused) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "정지한 기록이 있다면 공개할 수 없습니다.");
        }
    }

    public void verifyCourseId(Long courseId) {
        boolean isInvalid = (courseId == null || !courseId.equals(this.course.getId()));
        if (isInvalid) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "고스트가 뛴 코스가 아닙니다.");
        }
    }

}
