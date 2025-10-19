package soma.ghostrunner.domain.course.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CourseReadModelRepositoryTest extends IntegrationTestSupport {

    @Autowired
    CourseReadModelRepository courseReadModelRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    RunningRepository runningRepository;

    @DisplayName("코스 ID 안에 있는 러닝들을 조회한다.")
    @Test
    void findAllByCourseId() {
        // given
        Member member1 = createMember("이복둥");
        Member member2 = createMember("이복둥 주인");
        Member member3 = createMember("이복둥 아빠");
        Member member4 = createMember("이복둥 엄마");
        memberRepository.saveAll(List.of(member1, member2, member3, member4));

        Course c1 = createCourse(member1);
        Course c2 = createCourse(member2);
        Course c3 = createCourse(member4);
        courseRepository.saveAll(List.of(c1, c2, c3));

        List<Running> runs = new ArrayList<>();
        runs.add(createRunning(member1, c1));
        runs.add(createRunning(member2, c1));
        runs.add(createRunning(member2, c2));
        runs.add(createRunning(member3, c2));
        runs.add(createRunning(member4, c2));
        runningRepository.saveAll(runs);

        // when
        Set<Long> targetCourseIds = Set.of(c1.getId(), c2.getId(), c3.getId());
        List<Long> result = courseReadModelRepository.findMemberRunningIdsInCourses(targetCourseIds, member2.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result).contains(c1.getId(), c2.getId());
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

    private Running createRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", "URL", "URL", testMember, testCourse);
    }

    private RunningRecord createRunningRecord(long duration) {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, duration, 56, 100, 120);
    }

}
