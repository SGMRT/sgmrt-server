package soma.ghostrunner.learning;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.infra.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;
import java.util.NoSuchElementException;

public class JpaModifyingTest extends IntegrationTestSupport {

    @Autowired
    RunningRepository runningRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    CourseRepository courseRepository;

    @DisplayName("JPA deleteAll()은 N번 만큼 삭제 쿼리가 날라간다.")
    @Test
    void deleteAll() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running1 = createRunning(member, course);
        Running running2 = createRunning(member, course);
        Running running3 = createRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        runningRepository.deleteAll(List.of(running1, running2, running3));

        // then
        runningRepository.flush();
     }

    @DisplayName("@Modifying을 clearAutomatically 없이 적용하면 1차 캐시를 타지 않고 DB에 직접 WRITE를 찌르기 때문에 " +
            "다시 조회한다면 1차 캐시에서 조회하여 DB와 동기화 문제가 발생한다.")
    @Test
    void deleteAllByIdInNonClearAutomatically() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
//        runningRepository.deleteAllByIdInNonClearAutomatically(List.of(running.getId()));

        // then
//        Assertions.assertThat(runningRepository.findById(running.getId()).get()).isNotNull();
    }

    @DisplayName("@Modifying의 clearAutomatically = true를 적용하면 DB에 직접 WRITE를 찌르며 1차 캐시까지 비워주기 때문에 " +
            "다시 조회한다면 DB에서 다시 조회해서 동기화가 가능하다.")
    @Test
    void deleteAllByIdIn() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        runningRepository.deleteInRunningIds(List.of(running.getId()));

        // then
        Assertions.assertThatThrownBy(() -> runningRepository.findById(running.getId()).get())
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("No value present");
    }

    private Running createRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = RunningRecord.of(
                5.2, 40.0, 20.0, 5.1,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", "URL", "URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse(Member member) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        return Course.of(member, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "Mock URL", "Mock URL", "Mock URL");
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40.0, 40.1, -10.0);
    }

}
