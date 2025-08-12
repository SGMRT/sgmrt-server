package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.lang.reflect.Field;

class RunningTest {

    @DisplayName("이름을 변경한다.")
    @Test
    void updateName() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        Running running = createRunning("테스트 러닝제목", member, course);

        // when
        running.updateName("업데이트된 이름");

        // then
        Assertions.assertThat(running.getRunningName()).isEqualTo("업데이트된 이름");
    }

    private Member createMember() {
        return Member.of("이복둥", "프로필 URL");
    }

    private Course createCourse(Member member) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        return Course.of(member, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "Mock URL", "Mock URL");
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 30.0, 40.0, -20.0);
    }

    private Running createRunning(String runningName, Member testMember, Course testCourse) {
        return Running.of(runningName, RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                true, false, "URL", "URL", "URL", testMember, testCourse);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(
                5.2, 40.0, 30.0, -20.0,
                6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    @DisplayName("공개/비공개 설정을 변경한다.")
    @Test
    void updatePublicStatus() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        Running publicRunning = createRunning(
                "테스트 러닝제목", member, course, true, false);
        Running privateRunning = createRunning(
                "테스트 러닝제목", member, course, false, false);

        // when
        publicRunning.updatePublicStatus();
        privateRunning.updatePublicStatus();

        // then
        Assertions.assertThat(publicRunning.isPublic()).isFalse();
        Assertions.assertThat(privateRunning.isPublic()).isTrue();
    }

    @DisplayName("정지한 기록이 있다면 공개로 변경할 수 없다.")
    @Test
    void cannotUpdatePublicStatusIfHasPaused() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        Running hasPausedAndPrivateRunning = createRunning("테스트 러닝제목", member, course, false, true);

        // when // then
        Assertions.assertThatThrownBy(hasPausedAndPrivateRunning::updatePublicStatus)
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("정지한 기록이 있다면 공개할 수 없습니다.");
    }

    private Running createRunning(
            String runningName, Member testMember, Course testCourse, boolean isPublic, boolean hasPaused) {
        return Running.of(runningName, RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                isPublic, hasPaused, "URL", "URL", "URL", testMember, testCourse);
    }

    @DisplayName("뛰었던 코스의 ID인지 검증한다.")
    @Test
    void validateBelongsToCourse() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        setCourseId(course, 100L);
        Running running = createRunning("테스트 러닝제목", member, course);

        // when // then
        running.validateBelongsToCourse(100L);
    }

    @DisplayName("뛰었던 코스의 ID가 아니거나 NULL이라면 예외를 발생한다.")
    @Test
    void verifyIncorrectAndNullCourseId() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        setCourseId(course, 100L);
        Running running = createRunning("테스트 러닝제목", member, course);

        // when // then
        Assertions.assertThatThrownBy(() -> running.validateBelongsToCourse(101L))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("고스트가 뛴 코스가 아닙니다.");

        Assertions.assertThatThrownBy(() -> running.validateBelongsToCourse(null))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("고스트가 뛴 코스가 아닙니다.");
    }

    private void setCourseId(Course course, Long id) {
        try {
            Field idField = course.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(course, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
  
}
