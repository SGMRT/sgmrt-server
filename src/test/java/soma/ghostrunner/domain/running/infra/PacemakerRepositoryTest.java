package soma.ghostrunner.domain.running.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PacemakerRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private PacemakerRepository pacemakerRepository;

    @Autowired
    private PacemakerSetRepository pacemakerSetRepository;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Running createRunning(Member testMember, Course testCourse, String runningName) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of(runningName, RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL","URL","URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse(Member testMember) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "URL", "URL", "URL");
        course.setIsPublic(true);
        return course;
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 30.0, 40.0, -10.0);
    }

}
