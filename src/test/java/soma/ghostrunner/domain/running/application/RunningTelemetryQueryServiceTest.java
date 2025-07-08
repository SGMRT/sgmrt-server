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

import java.util.List;

class RunningTelemetryQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningTelemetryQueryService runningTelemetryQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    RunningRepository runningRepository;

    @DisplayName("코스에서 가장 처음 뛴 러너의 시계열 위치 데이터를 조회한다.")
    @Test
    void test() {
        // given
        Member member1 = createMember("이복둥");
        Member member2 = createMember("이복둥2");
        memberRepository.saveAll(List.of(member1, member2));

        Course course = createCourse();
        courseRepository.save(course);

        Running firstRunning = createRunning("첫 번째 러닝", course, member1);
        Running secondRunning = createRunning("두 번째 러닝", course, member1);
        Running thirdRunning = createRunning("세 번째 러닝", course, member2);
        runningRepository.saveAll(List.of(firstRunning, secondRunning, thirdRunning));

        // when
        Running running = runningTelemetryQueryService.findFirstRunning(course.getId());

        // then
        Assertions.assertThat(running.getId()).isEqualTo(firstRunning.getId());
     }

    private Running createRunning(String runningName, Course course, Member member) {
        return Running.of(runningName, RunningMode.SOLO, null, createRunningRecord(), 1750729987181L,
                true, false, "URL", member, course);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
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
