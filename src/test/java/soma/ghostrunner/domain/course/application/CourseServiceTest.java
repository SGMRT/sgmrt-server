package soma.ghostrunner.domain.course.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.CoursePreviewDto;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.exception.CourseAlreadyPublicException;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.List;
import java.util.Set;

import static soma.ghostrunner.domain.course.dto.request.CoursePatchRequest.UpdatedAttr.*;


class CourseServiceTest extends IntegrationTestSupport {

    @Autowired private CourseService courseService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private MemberRepository memberRepository;

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
    void searchCourses() {
        // given
        Course courseNearby1 = createPublicCourse("코스 1", LAT, LNG);
        Course courseNearby2 = createPublicCourse("코스 2", LAT + 0.001, LNG - 0.001);
        Course courseFar = createPublicCourse("먼 코스", LAT + 1, LNG + 1);
        courseRepository.saveAll(List.of(courseNearby1, courseNearby2, courseFar));

        // when
        List<CoursePreviewDto> courses = courseService.searchCourses(LAT, LNG, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of());

        // then
        // - course1, 2는 조회되고, course3은 조회되지 않는다
        Assertions.assertThat(courses).hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder(courseNearby1.getName(), courseNearby2.getName());
    }

    @DisplayName("코스 조회 시 검색 반경이 0일 경우 정확히 검색한 지점의 코스만 조회한다.")
    @Test
    void searchCourses_ZeroRadius() {
        // given
        Course course1 = createPublicCourse("코스 1", LAT, LNG);
        Course course2 = createPublicCourse("코스 2", LAT + 0.001, LNG - 0.001);
        courseRepository.saveAll(List.of(course1, course2));

        // when
        List<CoursePreviewDto> courses = courseService.searchCourses(LAT, LNG, 0, CourseSortType.DISTANCE, CourseSearchFilterDto.of());

        // then
        Assertions.assertThat(courses).hasSize(1)
                .extracting("name")
                .containsExactly(course1.getName());
    }

    @DisplayName("코스가 비공개 상태인 경우 조회 시 반경 내에 있어도 조회할 수 없다.")
    @Test
    void searchCourses_IsPublicFalse() {
        // given
        Course publicCourse = createPublicCourse("나를 찾아줘", LAT, LNG);
        Course privateCourse = createPrivateCourse("나를 찾지마", LAT, LNG);
        courseRepository.saveAll(List.of(publicCourse, privateCourse));

        // when
        List<CoursePreviewDto> courses = courseService.searchCourses(LAT, LNG, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of());

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
        List<CoursePreviewDto> courses = courseService.searchCourses(LAT, 0d, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of());

        // then
        // 동경, 서경 코스는 모두 조회되고, 멀리 있는 코스는 조회되지 않아야 함
        Assertions.assertThat(courses).hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder(courseEast.getName(), courseWest.getName());
    }

    @DisplayName("날짜 변경선 (경도 180도) 근처에서 코스를 검색하더라도 올바르게 조회할 수 있다.")
    @Test
    void searchCourses_DateLine() {
        // given
        // 날짜 변경선 근처인 동경(양수) 끝과 서경(음수) 끝에 코스를 생성
        Course courseEast = createPublicCourse("동경 끝 코스", LAT, 179.999);
        Course courseWest = createPublicCourse("서경 끝 코스", LAT, -179.999);
        Course courseFar = createPublicCourse("먼 코스", LAT, 0);
        courseRepository.saveAll(List.of(courseEast, courseWest, courseFar));

        // when
        // 동경 179.9985도 지점에서 반경 1km 내 코스 검색
        List<CoursePreviewDto> courses = courseService.searchCourses(LAT, 179.9985, 1000, CourseSortType.DISTANCE, CourseSearchFilterDto.of());

        // then
        // 동경 끝, 서경 끝 코스는 모두 조회되고, 멀리 있는 코스는 조회되지 않아야 함
        // todo
//        Assertions.assertThat(courses).hasSize(2)
//                .extracting("name")
//                .containsExactlyInAnyOrder(courseEast.getName(), courseWest.getName());
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
        courseService.updateCourse(id, request);

        // then
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getName()).isEqualTo(privateCourse.getName());
        Assertions.assertThat(course.getIsPublic()).isEqualTo(privateCourse.getIsPublic());
    }

    @DisplayName("코스 변경 DTO의 필드가 일부만 존재하는 경우 해당 필드만 수정한다.")
    @Test
    void updateCourse_PartialUpdate() {
        // given
        Course privateCourse = createPrivateCourse("나를 바꿔줘", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest("바꿨다", null, Set.of(NAME));

        // when
        courseService.updateCourse(id, request);

        // then
        Course course = courseRepository.findById(id).orElseThrow();
        Assertions.assertThat(course.getName()).isEqualTo(privateCourse.getName());
        Assertions.assertThat(course.getIsPublic()).isFalse();
    }

    @DisplayName("코스 제목을 빈칸으로 수정하면 예외가 발생한다.")
    @Test
    void updateCourse_CannotSetCourseNameNull() {
        // given
        Course privateCourse = createPrivateCourse("제목", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest(" ", null, Set.of(NAME));

        // when & then
        Assertions.assertThatThrownBy(() -> courseService.updateCourse(id, request))
                .isInstanceOf(CourseNameNotValidException.class)
                .hasMessage("invalid course name");
    }

    @DisplayName("코스가 이미 공개 상태인 경우 비공개 상태로 수정하면 예외가 발생한다.")
    @Test
    void updateCourse_CannotSetIsPublicToFalse() {
        // given
        Course privateCourse = createPublicCourse("공개 코스", LAT, LNG);
        Long id = courseRepository.save(privateCourse).getId();
        CoursePatchRequest request = new CoursePatchRequest(null, false, Set.of(IS_PUBLIC));

        // when & then
        Assertions.assertThatThrownBy(() -> courseService.updateCourse(id, request))
                .isInstanceOf(CourseAlreadyPublicException.class)
                .hasMessageContaining("already public");
    }

    @DisplayName("코스의 id를 기반으로 코스를 삭제할 수 있다.")
    @Test
    void deleteCourses() {
        // given
        Course course = createPublicCourse("코스명", LAT, LNG);
        Long id = courseRepository.save(course).getId();

        // when
        courseService.deleteCourse(id);

        // then
        Assertions.assertThat(courseRepository.findById(id)).isNotPresent();
    }

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

}
