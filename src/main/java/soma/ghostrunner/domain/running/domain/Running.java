package soma.ghostrunner.domain.running.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.common.BaseTimeEntity;
import soma.ghostrunner.global.error.ErrorCode;

import java.math.BigDecimal;

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

    /**
     * 코스 러닝 이벤트 생성
     *
     * - 기존 코스에서 러닝을 완료했을 때 발행되는 이벤트
     * - 코스 소유자에게 알림을 보내기 위해 사용
     *
     * @return CourseRunEvent
     * @throws IllegalStateException ID가 없는 경우
     */
    public CourseRunEvent createCourseRunEvent() {
        if (this.id == null) {
            throw new IllegalStateException("ID가 없으면 이벤트를 생성할 수 없습니다. save() 후에 호출하세요.");
        }

        return new CourseRunEvent(
                course != null ? course.getId() : null,
                course != null ? course.getName() : null,
                course != null && course.getMember() != null ? course.getMember().getId() : null,
                id,
                startedAt,
                runningRecord != null ? runningRecord.getDuration() : null,
                member != null ? member.getId() : null,
                member != null ? member.getNickname() : null
        );
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

        // 역방향 컬렉션(member.getRuns())에는 담지 않는다.
        // contains()가 PersistentBag 를 강제 초기화해 러닝 생성마다 그 회원의 전체 러닝을 SELECT 했고,
        // member 가 detached 면 LazyInitializationException 이 났다. 저장은 runningRepository.save 가 직접 한다.
        return Running.builder()
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
