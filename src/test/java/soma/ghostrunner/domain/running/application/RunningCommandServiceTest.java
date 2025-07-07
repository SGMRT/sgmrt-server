package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

class RunningCommandServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningCommandService runningCommandService;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @DisplayName("러닝 기록을 공개/비공개 상태로 변경한다.")
    @Test
    void setRunningPublic() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running publicRunning = runningRepository.save(createRunning(member, course, true));
        Running privateRunning = runningRepository.save(createRunning(member, course, false));

        // when
        runningCommandService.updateRunningPublicStatus(publicRunning.getId());
        runningCommandService.updateRunningPublicStatus(privateRunning.getId());

        // then
        Running updatedToPublicRunning = runningRepository.findById(privateRunning.getId()).get();
        Assertions.assertThat(updatedToPublicRunning.isPublic()).isTrue();

        Running updatedToPrivateRunning = runningRepository.findById(publicRunning.getId()).get();
        Assertions.assertThat(updatedToPrivateRunning.isPublic()).isFalse();
    }

    @DisplayName("러닝을 중지한 기록이 있다면 공개 설정이 불가능하다.")
    @Test
    void cannotEnablePublicWhenStoppedRunExists() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running hasPausedRunning = runningRepository.save(createHasPausedRunning(member, course));
        runningRepository.save(hasPausedRunning);

        // when // then
        Assertions.assertThatThrownBy(() -> runningCommandService.updateRunningPublicStatus(hasPausedRunning.getId()))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("정지한 기록이 있다면 공개할 수 없습니다.");
     }

    private Running createRunning(Member member, Course course, Boolean isPublic) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                isPublic, false, "URL", member, course);
    }

    private Running createHasPausedRunning(Member member, Course course) {
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                false, true, "URL", member, course);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse() {
        return Course.of(createCourseProfile(), createStartPoint(),
                "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    private StartPoint createStartPoint() {
        return StartPoint.fromCoordinates(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -10);
    }

}
