package soma.ghostrunner.domain.running.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;

@DataJpaTest
@ActiveProfiles("test")
class RunningRepositoryTest {

    @Autowired
    private RunningRepository runningRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Course savedCourse1;
    private Course savedCourse2;

    @BeforeEach
    void setUp() {
        // 코스 생성
        savedCourse1 = courseRepository.save(createCourse("테스트 코스1"));
        savedCourse2 = courseRepository.save(createCourse("테스트 코스2"));

        // 멤버 생성
        Member member = memberRepository.save(createMember("멤버"));

        // 러닝
        runningRepository.save(createRunning(member, savedCourse1));
        runningRepository.save(createRunning(member, savedCourse1));
        runningRepository.save(createRunning(member, savedCourse2));
    }

    @DisplayName("특정 코스 ID에 해당하는 모든 러닝 ID 목록을 조회한다")
    @Test
    void testSaveAndSelect() {
        // given
        Long targetCourseId = savedCourse1.getId();

        // when
        List<Long> runningIds = runningRepository.findIdsByCourseId(targetCourseId);

        // then
        Assertions.assertThat(runningIds).isNotNull();
        Assertions.assertThat(runningIds).hasSize(2);
        Assertions.assertThat(runningIds.contains(5L)).isFalse();
    }

    private Running createRunning(Member testMember, Course testCourse ) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, 6.1, 3423, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse(String courseName) {
        CourseMetaInfo testCourseMetaInfo = CourseMetaInfo.of(5.2, 40);
        StartPoint testStartPoint = StartPoint.fromCoordinates(37.545354, 34.7878);
        return Course.of(testCourseMetaInfo, testStartPoint, "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    @DisplayName("코스에 대해서 고스트가 러닝한 ID가 있는지 확인한다.")
    @Test
    void testFindGhostRunningIdWithCourse() {
        // given
        List<Long> course1RunningIds = runningRepository.findIdsByCourseId(savedCourse1.getId());
        List<Long> course2RunningIds = runningRepository.findIdsByCourseId(savedCourse2.getId());
        List<Long> noneRunningIds = runningRepository.findIdsByCourseId(10L);

        // then
        Assertions.assertThat(course1RunningIds).hasSize(2);
        Assertions.assertThat(course2RunningIds).isNotNull();
        Assertions.assertThat(noneRunningIds).hasSize(0);
    }

}
