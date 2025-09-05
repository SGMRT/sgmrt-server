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

    @DisplayName("러닝 ID에 있는 모든 페이스메이커를 삭제한다.")
    @Test
    void deletePacemakersInRunningIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running1 = createRunning(member, course, "러닝 제목1");
        Running running3 = createRunning(member, course, "러닝 제목3");
        List<Running> runnings = List.of(running1, running3);
        runningRepository.saveAll(runnings);

        Pacemaker pacemaker1 = Pacemaker.of("페이스메이커1 요약", 10.0, 60,
                "페이스메이커1 메세지입니다.", running1.getId());
        Pacemaker pacemaker3 = Pacemaker.of("페이스메이커3 요약", 10.0, 60,
                "페이스메이커3 메세지입니다.", running3.getId());
        pacemakerRepository.saveAll(List.of(pacemaker1, pacemaker3));

        PacemakerSet pacemaker1Set1 = PacemakerSet.of(
                1, "첫 세트 - 워밍업",
                0.0, 1.0, 6.0, pacemaker1
        );
        PacemakerSet pacemaker1Set2 = PacemakerSet.of(
                2, "두 번째 세트 - 본훈련",
                2.0, 4.0, 5.5, pacemaker1
        );
        PacemakerSet pacemaker3Set1 = PacemakerSet.of(
                1, "첫 세트 - 워밍업",
                0.0, 1.0, 6.0, pacemaker3
        );
        PacemakerSet pacemaker3Set2 = PacemakerSet.of(
                2, "두 번째 세트 - 본훈련",
                2.0, 4.0, 5.5, pacemaker3
        );
        pacemakerSetRepository.saveAll(List.of(pacemaker1Set1, pacemaker1Set2, pacemaker3Set1, pacemaker3Set2));

        // when
        pacemakerSetRepository.deletePacemakerSetsInRunningIds(List.of(running1.getId(), running3.getId()));
        pacemakerRepository.deletePacemakersInRunningIds(List.of(running1.getId(), running3.getId()));

        // then
        List<Pacemaker> deletedPacemakers = pacemakerRepository.findAllById(List.of(pacemaker1.getId(), pacemaker3.getId()));
        assertThat(deletedPacemakers).hasSize(0);

        List<PacemakerSet> deletedPacemakerSets = pacemakerSetRepository.findAllById(
                List.of(pacemaker1Set1.getId(), pacemaker1Set2.getId(), pacemaker3Set1.getId(), pacemaker3Set2.getId()));
        assertThat(deletedPacemakerSets).hasSize(0);
    }

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
