package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.common.document.TestOnly;
import soma.ghostrunner.global.error.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

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
    private RunningDataUrls runningDataUrls;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    @DomainEvents
    public Collection<Object> events() {
        return Collections.unmodifiableList(domainEvents);
    }

    @AfterDomainEventPublication
    public void clearEvents() {
        this.domainEvents.clear();
    }

    @PostPersist
    private void onPersisted() {
        domainEvents.add(new RunFinishedEvent(
                id,
                course != null ? course.getId() : null,
                member != null ? member.getUuid() : null,
                runningRecord.getAveragePace()
        ));
    }

    @PostUpdate
    private void onUpdated() {
        domainEvents.add(new RunUpdatedEvent(
                id,
                course != null ? course.getId() : null,
                member != null ? member.getUuid() : null,
                runningName,
                isPublic
        ));
    }

    @Builder(access = AccessLevel.PRIVATE)
    private Running(String runningName, RunningMode runningMode, Long ghostRunningId,
                    RunningRecord runningRecord, Long startedAt, boolean isPublic, boolean hasPaused,
                    RunningDataUrls runningDataUrls, Member member, Course course) {
        this.runningName = runningName;
        this.runningMode = runningMode;
        this.ghostRunningId = ghostRunningId;
        this.runningRecord = runningRecord;
        this.startedAt = startedAt;
        this.isPublic = isPublic;
        this.hasPaused = hasPaused;
        this.runningDataUrls = runningDataUrls;
        this.member = member;
        this.course = course;
    }

    public static Running of(String runningName, RunningMode runningMode, Long ghostRunningId,
                             RunningRecord runningRecord, Long startedAt, boolean isPublic, boolean hasPaused,
                             String rawTelemetrySavedUrl, String interpolatedTelemetrySavedUrl, String screenShotSavedUrl,
                             Member member, Course course) {

        RunningDataUrls runningDataUrls = RunningDataUrls.of(
                rawTelemetrySavedUrl, interpolatedTelemetrySavedUrl, screenShotSavedUrl);

        Running running = Running.builder()
                .runningName(runningName)
                .runningMode(runningMode)
                .ghostRunningId(ghostRunningId)
                .runningRecord(runningRecord)
                .startedAt(startedAt)
                .isPublic(isPublic)
                .hasPaused(hasPaused)
                .runningDataUrls(runningDataUrls)
                .member(member)
                .course(course)
                .build();

        List<Running> runs = running.member.getRuns();
        if (runs != null && !runs.contains(running)) {
            runs.add(running);
        }

        return running;
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

    public void validateBelongsToCourse(Long courseId) {
        boolean isInvalid = (courseId == null || !courseId.equals(this.course.getId()));
        if (isInvalid) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "고스트가 뛴 코스가 아닙니다.");
        }
    }

    public void verifyMember(String memberUuid) {
        if (!this.member.getUuid().equals(memberUuid)) {
            throw new AccessDeniedException("접근할 수 없는 러닝 데이터입니다.");
        }
    }

    public void updateScreenShotUrl(String screenShotUrl) {
        this.getRunningDataUrls().updateScreenShotUrl(screenShotUrl);
    }

    public static double calculateOneMilePace(double averagePace) {
        BigDecimal bigDecimalAveragePace = BigDecimal.valueOf(averagePace);
        BigDecimal oneMilePace = bigDecimalAveragePace.multiply(BigDecimal.valueOf(1.6));
        return oneMilePace.doubleValue();
    }

    public static double calculatePaceFromRunningLevel(String runningLevel) {
        switch (runningLevel) {
            case "입문자" -> {
                return calculateOneMilePace(8.0);
            }
            case "중급자" -> {
                return calculateOneMilePace(6.0);
            }
            case "상급자" -> {
                return calculateOneMilePace(5.0);
            }
            default -> {
                throw new IllegalArgumentException("올바르지 않은 러닝 레벨입니다.");
            }
        }
    }

}
