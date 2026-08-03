package soma.ghostrunner.domain.course.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.CoursePreviewDto;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static soma.ghostrunner.domain.course.dto.request.CoursePatchRequest.UpdatedAttr.*;


class CourseServiceTest extends IntegrationTestSupport {

    @Autowired private CourseService courseService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private RunningRepository runningRepository;
    @Autowired private CourseReadModelRepository readModelRepository;

    private Member dummyMember;
    private final CourseProfile dummyCourseInfo = CourseProfile.of(100d, 0d,0d, 0d);
    private final double LAT = 37.54324;
    private final double LNG = 126.94979;
    private final double KM_PER_LAT = 111; // 위도 1도 당 약 111km
    private final double KM_PER_LNG = 89; // 한국 기준 1도 당 약 89km

    @BeforeEach
    void setUp() {
        dummyMember = Member.of("카리나",  "karina.png");
        memberRepository.save(dummyMember);
    }


    @DisplayName("주어진 위경도 반경 내의 공개된 코스 목록을 정상적으로 조회한다.")
    @Test
    void findNearbyCourses() {
        // given
        Course courseNearby1 = createPublicCourse("코스 1", LAT, LNG);
        Course courseNearby2 = createPublicCourse("코스 2", LAT + 0.001, LNG - 0.001);
        Course courseFar = createPublicCourse("먼 코스", LAT + 1, LNG + 1);
        courseRepository.saveAll(List.of(courseNearby1, courseNearby2, courseFar));

        // when
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(LAT, LNG, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of(), dummyMember.getId());

        // then
        // - course1, 2는 조회되고, course3은 조회되지 않는다
        Assertions.assertThat(courses).hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder(courseNearby1.getName(), courseNearby2.getName());
    }

    @DisplayName("코스 조회 시 검색 반경이 0일 경우 정확히 검색한 지점의 코스만 조회한다.")
    @Test
    void findNearbyCourses_ZeroRadius() {
        // given
        Course course1 = createPublicCourse("코스 1", LAT, LNG);
        Course course2 = createPublicCourse("코스 2", LAT + 0.001, LNG - 0.001);
        courseRepository.saveAll(List.of(course1, course2));

        // when
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(LAT, LNG, 0, CourseSortType.DISTANCE, CourseSearchFilterDto.of(), dummyMember.getId());

        // then
        Assertions.assertThat(courses).hasSize(1)
                .extracting("name")
                .containsExactly(course1.getName());
    }

    @DisplayName("코스가 비공개 상태인 경우 조회 시 반경 내에 있어도 조회할 수 없다.")
    @Test
    void findNearbyCourses_IsPublicFalse() {
        // given
        Course publicCourse = createPublicCourse("나를 찾아줘", LAT, LNG);
        Course privateCourse = createPrivateCourse("나를 찾지마", LAT, LNG);
        courseRepository.saveAll(List.of(publicCourse, privateCourse));

        // when
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(LAT, LNG, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of(), dummyMember.getId());

        // then
        Assertions.assertThat(courses).hasSize(1);
        Assertions.assertThat(courses.get(0).name()).isEqualTo(publicCourse.getName());
    }

    @DisplayName("본초자오선 (경도 0도) 근처에서 코스를 검색하더라도 올바르게 조회할 수 있다.")
    @Test
    void searchCourse_PrimeMeridian() {
        // given
        Course courseEast = createPublicCourse("동경 코스", LAT, 0.001);
        Course courseWest = createPublicCourse("서경 코스", LAT, -0.001);
        Course courseFar = createPublicCourse("먼 코스", LAT, 0.1);
        courseRepository.saveAll(List.of(courseEast, courseWest, courseFar));

        // when
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(LAT, 0d, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of(), dummyMember.getId());

        // then
        // 동경, 서경 코스는 모두 조회되고, 멀리 있는 코스는 조회되지 않아야 함
        Assertions.assertThat(courses).hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder(courseEast.getName(), courseWest.getName());
    }

    @DisplayName("날짜 변경선 (경도 180도) 근처에서 코스를 검색하더라도 올바르게 조회할 수 있다.")
    @Test
    void findNearbyCourses_DateLine() {
        // given
        // 날짜 변경선 근처인 동경(양수) 끝과 서경(음수) 끝에 코스를 생성
        Course courseEast = createPublicCourse("동경 끝 코스", LAT, 179.999);
        Course courseWest = createPublicCourse("서경 끝 코스", LAT, -179.999);
        Course courseFar = createPublicCourse("먼 코스", LAT, 0);
        courseRepository.saveAll(List.of(courseEast, courseWest, courseFar));

        // when
        // 동경 179.9985도 지점에서 반경 1km 내 코스 검색
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(LAT, 179.9985, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of(), dummyMember.getId());

        // then
        // 동경 끝, 서경 끝 코스는 모두 조회되고, 멀리 있는 코스는 조회되지 않아야 함
        // todo
//        Assertions.assertThat(courses).hasSize(2)
//                .extracting("name")
//                .containsExactlyInAnyOrder(courseEast.getName(), courseWest.getName());
    }

    @DisplayName("본초자오선 기준으로 위경도가 이루는 4분면 어디에서도 코스를 조회할 수 있다.")
    @ParameterizedTest(name = "[{index}] {0}에서 코스를 검색하면 올바른 코스가 조회된다.")
    @MethodSource("locationQuadrants")
    void findNearbyCourses_quadrants(String name, double lat, double lng) {
        // given
        Course courseNearby = createPublicCourse("근처 코스", lat + 0.001, lng + 0.001);
        Course courseFar = createPublicCourse("먼 코스", lat + 1.0, lng + 1.0);
        courseRepository.saveAll(List.of(courseNearby, courseFar));

        // when
        List<CoursePreviewDto> courses = courseService.findNearbyCourses(lat, lng, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of(), dummyMember.getId());

        // then
        assertThat(courses).hasSize(1)
                .extracting("name")
                .containsExactly(courseNearby.getName());
    }

    private static Stream<Arguments> locationQuadrants() {
        return Stream.of(
                Arguments.of("북동쪽 (1사분면)", 37.5, 127.5),
                Arguments.of("북서쪽 (2사분면)", 37.5, -127.5),
                Arguments.of("남서쪽 (3사분면)", -36.5, 127.5),
                Arguments.of("남동쪽 (4사분면)", -36.5, -127.5)
        );
    }


    @DisplayName("코스의 id를 기반으로 코스 상세 정보를 조회할 수 있다.")
    @Test
    void getCourseDetail() {
        // given
        Course course = createPublicCourse("course", LAT, LNG);
        Course savedCourse = courseRepository.save(course);

        // when
        Course foundCourse = courseService.findCourseById(savedCourse.getId());

        // then
        Assertions.assertThat(foundCourse.getId()).isEqualTo(savedCourse.getId());
    }

    @DisplayName("코스의 제목과 공개 여부를 수정할 수 있다.")
    @Test
    void updateCourse_Success() {
        // given
        Course privateCourse = createPrivateCourse("나를 바꿔줘", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest("바꿨다", true, Set.of(NAME, IS_PUBLIC));

        // when
        courseService.updateCourse(id, request, dummyMember.getUuid());

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
        courseService.updateCourse(id, request, dummyMember.getUuid());

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
        Assertions.assertThatThrownBy(() -> courseService.updateCourse(id, request, dummyMember.getUuid()))
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
        courseService.updateCourse(id, request, dummyMember.getUuid());

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
        courseService.updateCourse(id, request, dummyMember.getUuid());

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
        courseService.updateCourse(id, request, dummyMember.getUuid());

        // then - 여전히 비공개 상태 유지 (멱등성)
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getIsPublic()).isFalse();
    }

    @DisplayName("코스의 id를 기반으로 코스를 삭제할 수 있다.")
    @Test
    void deleteCourses() {
        // given
        Course course = createPublicCourse("코스명", LAT, LNG);
        Long id = courseRepository.save(course).getId();

        // when
        courseService.deleteCourse(id, dummyMember.getUuid());

        // then
        Assertions.assertThat(courseRepository.findById(id)).isNotPresent();
    }

    // --- Helper Methods ---
    private Course createPublicCourse(String name, double lat, double lng, CourseProfile courseProfile) {
        return createCourse(name, dummyMember, lat, lng, courseProfile, true);
    }

    private Course createPublicCourse(String name, double lat, double lng) {
        return createCourse(name, dummyMember, lat, lng, dummyCourseInfo, true);
    }

    private Course createPrivateCourse(String name, double lat, double lng) {
        return createCourse(name, dummyMember, lat, lng, dummyCourseInfo, false);
    }

    private Course createCourse(String name, Member member, double lat, double lng, CourseProfile courseProfile, boolean isPublic) {
        Course course = Course.of(member, 0d, 0d, 0d, 0d, lat, lng, "url", "url", "url");
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
        course.setName("테스트 코스");
        courseRepository.save(course);

        // 코스 주인의 러닝 기록 생성 (최고기록: 100초)
        Running bestRun = createRunning(course, dummyMember, 100L, false); // 100초, hasPaused=false
        Running slowerRun = createRunning(course, dummyMember, 200L, false); // 200초
        runningRepository.saveAll(List.of(bestRun, slowerRun));

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseService.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow(() -> new AssertionError("리드모델이 생성되지 않았습니다."));

        assertThat(readModel.getIsPublic()).isTrue();
        assertThat(readModel.getTop1MemberId()).isEqualTo(dummyMember.getId());
        assertThat(readModel.getTop1TimeSeconds()).isEqualTo(100); // 최고기록
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);

        // TOP2, TOP3, TOP4는 null
        assertThat(readModel.getTop2MemberId()).isNull();
        assertThat(readModel.getTop3MemberId()).isNull();
        assertThat(readModel.getTop4MemberId()).isNull();
    }

    @DisplayName("코스 재등록 시 기존 리드모델의 isPublic만 true로 변경된다.")
    @Test
    void reRegisterCourse_onlyUpdatesIsPublic() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        course.setName("테스트 코스");
        courseRepository.save(course);

        Running run = createRunning(course, dummyMember, 100L, false);
        runningRepository.save(run);

        // 처음 등록
        courseService.updateCourse(course.getId(),
                new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // 등록 해제
        courseService.updateCourse(course.getId(),
                new CoursePatchRequest(null, false, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        CourseReadModel readModelAfterUnregister = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();
        assertThat(readModelAfterUnregister.getIsPublic()).isFalse();

        // when - 재등록
        courseService.updateCourse(course.getId(),
                new CoursePatchRequest(null, true, Set.of(IS_PUBLIC)), dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();

        assertThat(readModel.getIsPublic()).isTrue();
        // 기존 TOP1 데이터 유지
        assertThat(readModel.getTop1MemberId()).isEqualTo(dummyMember.getId());
        assertThat(readModel.getTop1TimeSeconds()).isEqualTo(100);
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
                courseService.updateCourse(course.getId(), request, dummyMember.getUuid()))
                .isInstanceOf(CourseNameNotValidException.class);
    }

    @DisplayName("코스 등록 시 일시정지한 기록은 TOP1에서 제외된다.")
    @Test
    void registerCourse_excludesPausedRunFromTop1() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        course.setName("테스트 코스");
        courseRepository.save(course);

        // hasPaused=true인 기록은 제외
        Running pausedRun = createRunning(course, dummyMember, 50L, true); // 더 빠르지만 일시정지함
        Running validRun = createRunning(course, dummyMember, 100L, false); // 유효한 기록
        runningRepository.saveAll(List.of(pausedRun, validRun));

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseService.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();

        // 일시정지하지 않은 기록(100초)이 TOP1
        assertThat(readModel.getTop1TimeSeconds()).isEqualTo(100);
    }

    @DisplayName("코스 등록 시 일시정지한 기록만 있으면 TOP1은 null이지만 runnersCount는 정상 집계된다.")
    @Test
    void registerCourse_onlyPausedRuns_top1NullButRunnersCountCorrect() {
        // given
        Course course = createPrivateCourse("테스트 코스", LAT, LNG);
        course.setName("테스트 코스");
        courseRepository.save(course);

        // hasPaused=true인 기록만 존재
        Running pausedRun = createRunning(course, dummyMember, 50L, true);
        runningRepository.save(pausedRun);

        CoursePatchRequest request = new CoursePatchRequest(null, true, Set.of(IS_PUBLIC));

        // when
        courseService.updateCourse(course.getId(), request, dummyMember.getUuid());

        // then
        CourseReadModel readModel = readModelRepository.findByCourseId(course.getId())
                .orElseThrow();

        // TOP1은 null (hasPaused=false인 기록이 없으므로)
        assertThat(readModel.getTop1MemberId()).isNull();
        assertThat(readModel.getTop1TimeSeconds()).isNull();

        // runnersCount는 1 (isPublic=true인 기록이 있으므로)
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);
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
