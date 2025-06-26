package soma.ghostrunner.domain.running.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import soma.ghostrunner.domain.course.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;
import java.util.Optional;

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

    private Member member1;
    private Member member2;

    private Running running1;
    private Running running2;
    private Running running3;

    @BeforeEach
    void setUp() {
        // 코스 생성
        savedCourse1 = courseRepository.save(createCourse("테스트 코스1"));
        savedCourse2 = courseRepository.save(createCourse("테스트 코스2"));

        // 멤버 생성
        member1 = memberRepository.save(createMember("멤버1"));
        member2 = memberRepository.save(createMember("멤버2"));

        // 러닝
        running1 = runningRepository.save(createRunning(member1, savedCourse1));
        running2 = runningRepository.save(createRunning(member1, savedCourse1));
        running3 = runningRepository.save(createRunning(member1, savedCourse2));
    }

    @DisplayName("특정 코스 ID에 해당하는 모든 러닝 ID 목록을 조회한다")
    @Test
    void testSaveAndSelect() {
        // given
        Long targetCourseId = savedCourse1.getId();

        // when
        List<Long> runningIds = runningRepository.findIdsByCourseId(targetCourseId);

        // then
        Assertions.assertThat(runningIds.contains(running1.getId())).isTrue();
        Assertions.assertThat(runningIds).hasSize(2);
        Assertions.assertThat(runningIds.contains(5L)).isFalse();
    }

    @DisplayName("러닝 ID와 멤버 ID로 러닝을 조회한다.")
    @Test
    void testFindByRunningIdAndMemberId() {
        // when
        Running existRunning = runningRepository.findByRunningIdAndMemberId(running1.getId(), member1.getId()).get();
        Optional<Running> nonExistRunning1 = runningRepository.findByRunningIdAndMemberId(running2.getId(), member2.getId());
        Optional<Running> nonExistRunning2 = runningRepository.findByRunningIdAndMemberId(10L, member1.getId());

        // then
        Assertions.assertThat(existRunning.getId()).isEqualTo(running1.getId());
        Assertions.assertThat(nonExistRunning1.isPresent()).isFalse();
        Assertions.assertThat(nonExistRunning2.isPresent()).isFalse();
    }

    private Running createRunning(Member testMember, Course testCourse ) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse(String courseName) {
        CourseMetaInfo testCourseMetaInfo = CourseMetaInfo.of(5.2, 40, -10);
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
