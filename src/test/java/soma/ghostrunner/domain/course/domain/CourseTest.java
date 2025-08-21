package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Member;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Course 단위 테스트")
class CourseTest {

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.of("아이유", "profile.url");
    }

    @Test
    @DisplayName("팩토리 메소드 of()로 Course 객체를 생성한다. (1)")
    void createCourse_Success() {
        // when
        Course course = Course.of(member, 180.0, 10.0, 10.0, 10.0,
                37d, 129d, "route.url", "checkpoint.url", "thumb.url");

        // then
        assertThat(course).isNotNull();
        assertThat(course.getMember()).isEqualTo(member);
        assertThat(course.getCourseProfile().getDistance()).isEqualTo(180.0);
        assertThat(course.getCourseProfile().getElevationGain()).isEqualTo(10.0);
        assertThat(course.getCourseProfile().getElevationLoss()).isEqualTo(10.0);
        assertThat(course.getStartCoordinate().getLatitude()).isEqualTo(37);
        assertThat(course.getStartCoordinate().getLongitude()).isEqualTo(129);
        assertThat(course.getCourseDataUrls().getRouteUrl()).isEqualTo("route.url");
        assertThat(course.getCourseDataUrls().getThumbnailUrl()).isEqualTo("thumb.url");
        assertThat(course.getIsPublic()).isFalse(); // 초기값은 false
    }

    @Test
    @DisplayName("팩토리 메소드 of()로 Course 객체를 생성한다. (2)")
    void createCourse_Success_2() {
        // given
        Coordinate coord = Coordinate.of(37d, 129d);
        CourseProfile profile = CourseProfile.of(180.0, 10.0, 10.0, 10.0);
        CourseDataUrls urls = CourseDataUrls.of("route.url", "checkpoint.url", "thumb.url");

        // when
        Course course = Course.of(member, "Test Course", profile, coord, true, urls);

        // then
        // then
        assertThat(course).isNotNull();
        assertThat(course.getMember()).isEqualTo(member);
        assertThat(course.getCourseProfile().getDistance()).isEqualTo(180.0);
        assertThat(course.getCourseProfile().getElevationGain()).isEqualTo(10.0);
        assertThat(course.getCourseProfile().getElevationLoss()).isEqualTo(10.0);
        assertThat(course.getStartCoordinate().getLatitude()).isEqualTo(37);
        assertThat(course.getStartCoordinate().getLongitude()).isEqualTo(129);
        assertThat(course.getCourseDataUrls().getRouteUrl()).isEqualTo("route.url");
        assertThat(course.getCourseDataUrls().getThumbnailUrl()).isEqualTo("thumb.url");
        assertThat(course.getIsPublic()).isTrue(); // 초기값은 false
    }

    @Test
    @DisplayName("코스 이름을 정상적으로 변경할 수 있다.")
    void updateCourseName_Success() {
        // given
        Course course = createDefaultCourse();
        String newName = "새로운 코스명";

        // when
        course.setName(newName);

        // then
        assertThat(course.getName()).isEqualTo(newName);
    }

    @Test
    @DisplayName("비공개 코스를 공개로 변경할 수 있다.")
    void updateCoursePublicity_PrivateToPublic_Success() {
        // given
        Course course = createDefaultCourse();
        assertThat(course.getIsPublic()).isFalse(); // 초기 상태 확인

        // when
        course.setIsPublic(true);

        // then
        assertThat(course.getIsPublic()).isTrue();
    }

    private Course createDefaultCourse() {
        return Course.of(member, 5.0, 10.0, 100.0, -50.0, 37.123, 127.123, "route.url", "checkpoint.url", "thumb.url");
    }
}
