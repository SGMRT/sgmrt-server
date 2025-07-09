package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

class RunningTest {

    @DisplayName("이름을 변경한다.")
    @Test
    void updateName() {
        // given
        Running running = Running.of("테스트 러닝제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                true, false, "URL", createMember(), createCourse());

        // when
        running.updateName("업데이트된 이름");

        // then
        Assertions.assertThat(running.getRunningName()).isEqualTo("업데이트된 이름");
    }

    @DisplayName("공개/비공개 설정을 변경한다.")
    @Test
    void updatePublicStatus() {
        // given
        Running publicRunning = Running.of("테스트 러닝제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                true, false, "URL", createMember(), createCourse());
        Running privateRunning = Running.of("테스트 러닝제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                false, false, "URL", createMember(), createCourse());

        // when
        publicRunning.updatePublicStatus();
        privateRunning.updatePublicStatus();

        // then
        Assertions.assertThat(publicRunning.isPublic()).isFalse();
        Assertions.assertThat(privateRunning.isPublic()).isTrue();
    }

    @DisplayName("정지한 기록이 있다면 공개로 변경할 수 없다.")
    @Test
    void test() {
        // given
        Running hasPausedAndPrivateRunning = Running.of("테스트 러닝제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                false, true, "URL", createMember(), createCourse());

        // when // then
        Assertions.assertThatThrownBy(hasPausedAndPrivateRunning::updatePublicStatus)
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("정지한 기록이 있다면 공개할 수 없습니다.");
     }

    private Member createMember() {
        return Member.of("이복둥", "프로필 URL");
    }

    private Course createCourse() {
        return Course.of(createCourseProfile(), createStartPoint(), "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    private StartPoint createStartPoint() {
        return StartPoint.fromCoordinates(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -20);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

}
