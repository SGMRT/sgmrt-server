package soma.ghostrunner.domain.course.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static soma.ghostrunner.domain.course.dto.request.CoursePatchRequest.UpdatedAttr.*;

/**
 * 코스 쓰기 트랜잭션 경계({@link CourseWriter})의 통합 테스트 — 수정·삭제와 리드모델 동기화.
 * (구 CourseServiceTest 의 쓰기 절반. 조회는 CourseReaderTest 가 덮는다)
 */
class CourseWriterTest extends IntegrationTestSupport {

    @Autowired private CourseWriter courseWriter;
    @Autowired private CourseRepository courseRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private RunningRepository runningRepository;
    @Autowired private CourseReadModelRepository readModelRepository;
    @Autowired private CourseSubscriptionRepository subscriptionRepository;

    private Member dummyMember;
    private final double LAT = 37.54324;
    private final double LNG = 126.94979;

    @BeforeEach
    void setUp() {
        dummyMember = Member.of("카리나",  "karina.png");
        memberRepository.save(dummyMember);
    }

    @DisplayName("코스의 제목과 공개 여부를 수정할 수 있다.")
    @Test
    void updateCourse_Success() {
        // given
        Course privateCourse = createPrivateCourse("나를 바꿔줘", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest("바꿨다", true, Set.of(NAME, IS_PUBLIC));

        // when
        courseWriter.updateCourse(id, request, dummyMember.getUuid());

        // then
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getName()).isEqualTo("바꿨다");
        Assertions.assertThat(course.getIsPublic()).isTrue();
    }

    @DisplayName("코스 변경 DTO의 필드가 일부만 존재하는 경우 해당 필드만 수정한다.")
    @Test
    void updateCourse_PartialUpdate() {
        // given
        Course privateCourse = createPrivateCourse("나를 바꿔줘", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest("바꿨다", null, Set.of(NAME));

        // when
        courseWriter.updateCourse(id, request, dummyMember.getUuid());

        // then
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getName()).isEqualTo("바꿨다"); // 수정된 값
        Assertions.assertThat(course.getIsPublic()).isFalse(); // 그대로 유지
    }

    @DisplayName("코스 제목을 빈칸으로 수정하면 예외가 발생한다.")
    @Test
    void updateCourse_CannotSetCourseNameNull() {
        // given
        Course privateCourse = createPrivateCourse("제목", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest(" ", null, Set.of(NAME));

        // when & then
        Assertions.assertThatThrownBy(() -> courseWriter.updateCourse(id, request, dummyMember.getUuid()))
                .isInstanceOf(CourseNameNotValidException.class)
                .hasMessage("invalid course name");
    }

    @DisplayName("코스가 이미 공개 상태인 경우 비공개 상태로 수정할 수 있다 (등록 해제)")
    @Test
    void updateCourse_CanSetIsPublicToFalse_Unregister() {
        // given
        Course publicCourse = createPublicCourse("공개 코스", LAT, LNG);
        Long id = courseRepository.save(publicCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest(null, false, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(id, request, dummyMember.getUuid());

        // then - 등록 해제 성공
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getIsPublic()).isFalse();
    }

    @DisplayName("이미 공개 상태인 코스를 다시 공개로 수정해도 성공한다 (멱등성)")
    @Test
    void updateCourse_AlreadyPublic_Idempotent() {
        // given
        Course publicCourse = createPublicCourse("공개 코스", LAT, LNG);
        Long id = courseRepository.save(publicCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(id, request, dummyMember.getUuid());

        // then - 여전히 공개 상태 유지 (멱등성)
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getIsPublic()).isTrue();
    }

    @DisplayName("이미 비공개 상태인 코스를 다시 비공개로 수정해도 성공한다 (멱등성)")
    @Test
    void updateCourse_AlreadyPrivate_Idempotent() {
        // given
        Course privateCourse = createPrivateCourse("비공개 코스", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest(null, false, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(id, request, dummyMember.getUuid());

        // then - 여전히 비공개 상태 유지 (멱등성)
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getIsPublic()).isFalse();
    }

    /**
     * 주인 구독의 소프트 delete 복원은 "깨지면 실제로 아픈" 불변식이라 DB 레벨로 못박는다.
     * 재등록이 복원이 아니라 새 행 삽입으로 바뀌면 유니크 제약(uk_course_member)에 걸려 등록 자체가 막힌다.
     */
    @DisplayName("코스를 등록 → 해제 → 재등록해도 주인 구독은 같은 행을 복원해 쓰며 최종 상태는 활성이다.")
    @Test
    void ownerSubscription_isRestoredNotDuplicated_onReRegister() {
        // given
        Course course = createPrivateCourse("등록 사이클 코스", LAT, LNG);
        Long id = courseRepository.save(course).getId();

        // when 1 - 등록
        courseWriter.updateCourse(id, new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // then 1 - 주인 구독이 활성으로 생긴다
        assertThat(findOwnerSubscription(id).isActive()).isTrue();

        // when 2 - 등록 해제
        courseWriter.updateCourse(id, new CoursePatchRequest(null, false, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // then 2 - 행이 지워지는 게 아니라 soft delete 된다
        assertThat(findOwnerSubscription(id).isDeleted()).isTrue();

        // when 3 - 재등록
        courseWriter.updateCourse(id, new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // then 3 - 복원되어 활성이고, 새 행을 만들지 않았다
        assertThat(findOwnerSubscription(id).isActive()).isTrue();
        assertThat(subscriptionRepository.findAll()).hasSize(1);
    }

    private CourseSubscription findOwnerSubscription(Long courseId) {
        return subscriptionRepository.findByCourseIdAndMemberId(courseId, dummyMember.getId())
                .orElseThrow(() -> new AssertionError("주인의 구독이 존재하지 않습니다."));
    }

    @DisplayName("코스의 id를 기반으로 코스를 삭제할 수 있다.")
    @Test
    void deleteCourses() {
        // given
        Course course = createPublicCourse("코스명", LAT, LNG);
        Long id = courseRepository.save(course).getId();

        // when
        courseWriter.deleteCourse(id, dummyMember.getUuid());

        // then
        Assertions.assertThat(courseRepository.findById(id)).isNotPresent();
    }

    // --- Helper Methods ---
    private Course createPublicCourse(String name, double lat, double lng) {
        return createCourse(name, lat, lng, true);
    }

    private Course createPrivateCourse(String name, double lat, double lng) {
        return createCourse(name, lat, lng, false);
    }

    private Course createCourse(String name, double lat, double lng, boolean isPublic) {
        Course course = Course.of(dummyMember, 0d, 0d, 0d, 0d, lat, lng, "url", "url", "url");
        course.setName(name);
        course.setIsPublic(isPublic);
        return course;
    }

    // --- 리드모델 관련 테스트 ---

    @DisplayName("코스 등록 시 리드모델이 생성되고 주인의 최고기록이 TOP1으로 설정된다.")
    @Test
    void registerCourse_createsReadModelWithOwnerBestRecord() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        courseRepository.save(course);

        // 코스 주인의 러닝 기록 생성 (최고기록: 100초)
        Running bestRun = createRunning(course, dummyMember, 100L, false); // 100초, hasPaused=false
        Running slowerRun = createRunning(course, dummyMember, 200L, false); // 200초
        runningRepository.saveAll(List.of(bestRun, slowerRun));

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow(() -> new AssertionError("리드모델이 생성되지 않았습니다."));

        assertThat(readModel.getIsPublic()).isTrue();
        assertThat(readModel.getTop1().getMemberId()).isEqualTo(dummyMember.getId());
        assertThat(readModel.getTop1().getTimeSeconds()).isEqualTo(100); // 최고기록
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);

        // TOP2, TOP3, TOP4는 null
        assertThat(readModel.getTop2()).isNull();
        assertThat(readModel.getTop3()).isNull();
        assertThat(readModel.getTop4()).isNull();
    }

    @DisplayName("코스 재등록 시 기존 리드모델의 isPublic만 true로 변경된다.")
    @Test
    void reRegisterCourse_onlyUpdatesIsPublic() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        courseRepository.save(course);

        Running run = createRunning(course, dummyMember, 100L, false);
        runningRepository.save(run);

        // 처음 등록
        courseWriter.updateCourse(course.getId(),
                new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // 등록 해제
        courseWriter.updateCourse(course.getId(),
                new CoursePatchRequest(null, false, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        CourseReadModel readModelAfterUnregister = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();
        assertThat(readModelAfterUnregister.getIsPublic()).isFalse();

        // when - 재등록
        courseWriter.updateCourse(course.getId(),
                new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();

        assertThat(readModel.getIsPublic()).isTrue();
        // 기존 TOP1 데이터 유지
        assertThat(readModel.getTop1().getMemberId()).isEqualTo(dummyMember.getId());
        assertThat(readModel.getTop1().getTimeSeconds()).isEqualTo(100);
    }

    @DisplayName("코스 이름이 없는 상태에서 등록하려고 하면 예외가 발생한다.")
    @Test
    void registerCourse_withoutName_throwsException() {
        // given
        Course course = createPrivateCourse(null, LAT, LNG); // 이름 없음
        courseRepository.save(course);

        Running run = createRunning(course, dummyMember, 100L, false);
        runningRepository.save(run);

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when & then
        Assertions.assertThatThrownBy(() ->
                courseWriter.updateCourse(course.getId(), request, dummyMember.getUuid()))
                .isInstanceOf(CourseNameNotValidException.class);
    }

    @DisplayName("코스 등록 시 일시정지한 기록은 TOP1에서 제외된다.")
    @Test
    void registerCourse_excludesPausedRunFromTop1() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        courseRepository.save(course);

        // hasPaused=true인 기록은 제외
        Running pausedRun = createRunning(course, dummyMember, 50L, true); // 더 빠르지만 일시정지함
        Running validRun = createRunning(course, dummyMember, 100L, false); // 유효한 기록
        runningRepository.saveAll(List.of(pausedRun, validRun));

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();

        // 일시정지하지 않은 기록(100초)이 TOP1
        assertThat(readModel.getTop1().getTimeSeconds()).isEqualTo(100);
    }

    @DisplayName("코스명을 변경하면 리드모델의 이름도 함께 변경된다.")
    @Test
    void updateCourseName_syncsReadModelName() {
        // given - 공개 전환으로 리드모델이 만들어진 코스
        Course course = createPrivateCourse("옛 이름", LAT, LNG);
        courseRepository.save(course);
        runningRepository.save(createRunning(course, dummyMember, 100L, false));

        courseWriter.updateCourse(course.getId(),
                new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());
        assertThat(readModelRepository.findByCourseId(course.getId()).orElseThrow().getName())
                .isEqualTo("옛 이름");

        // when
        courseWriter.updateCourse(course.getId(),
                new CoursePatchRequest("새 이름", null, Set.of(NAME)), dummyMember.getUuid());

        // then
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getName()).isEqualTo("새 이름");
        assertThat(readModelRepository.findByCourseId(course.getId()).orElseThrow().getName())
                .isEqualTo("새 이름");
    }

    @DisplayName("코스 등록 시 주인보다 빠른 타인의 기록이 있으면 그 기록이 TOP1이 되고 러너 수도 전부 집계된다.")
    @Test
    void registerCourse_initializesReadModelFromAllRunners() {
        // given - 주인(2000초)보다 빠른 타인(1500초)의 공개 러닝이 이미 존재
        Member otherRunner = memberRepository.save(Member.of("윈터", "winter.png"));

        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        courseRepository.save(course);
        runningRepository.saveAll(List.of(
                createRunning(course, dummyMember, 2000L, false),
                createRunning(course, otherRunner, 1500L, false)));

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then - 주인 기록만 채우는 것이 아니라 전체 러닝으로 초기화된다
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow(() -> new AssertionError("리드모델이 생성되지 않았습니다."));

        assertThat(readModel.getTop1().getMemberId()).isEqualTo(otherRunner.getId());
        assertThat(readModel.getTop1().getTimeSeconds()).isEqualTo(1500);
        assertThat(readModel.getTop2().getMemberId()).isEqualTo(dummyMember.getId());
        assertThat(readModel.getTop2().getTimeSeconds()).isEqualTo(2000);
        assertThat(readModel.getRunnersCount()).isEqualTo(2L);
    }

    @DisplayName("코스 등록 시 일시정지한 기록만 있으면 TOP1도 null이고 runnersCount도 0이다.")
    @Test
    void registerCourse_onlyPausedRuns_top1NullButRunnersCountCorrect() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        courseRepository.save(course);

        // hasPaused=true인 기록만 존재
        Running pausedRun = createRunning(course, dummyMember, 50L, true);
        runningRepository.save(pausedRun);

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseWriter.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();

        // TOP1은 null (hasPaused=false인 기록이 없으므로)
        assertThat(readModel.getTop1()).isNull();

        // runnersCount도 0 — 재계산(Q1/Q4)은 TOP4와 러너 수를 같은 모집단(일시정지 제외)에서 뽑는다
        assertThat(readModel.getRunnersCount()).isZero();
    }

    private Running createRunning(Course course, Member member, Long durationSeconds, boolean hasPaused) {
        RunningRecord record = RunningRecord.of(
                5.0, 100.0, 50.0, 30.0,
                6.0, 5.0, 7.0, durationSeconds,
                300, 170, 80
        );
        return Running.of(
                "테스트 러닝",
                RunningMode.SOLO,
                null,
                record,
                System.currentTimeMillis(),
                true, // isPublic
                hasPaused,
                "rawUrl", "interpolatedUrl", "screenshotUrl",
                member,
                course
        );
    }

}
