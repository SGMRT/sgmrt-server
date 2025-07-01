package soma.ghostrunner.domain.running.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.global.config.QuerydslConfig;

import java.util.List;
import java.util.Optional;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
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
    private Running running4;

    @BeforeEach
    void setUp() {
        savedCourse1 = courseRepository.save(createCourse());
        savedCourse2 = courseRepository.save(createCourse());
        member1 = memberRepository.save(createMember("멤버1"));
        member2 = memberRepository.save(createMember("멤버2"));
        running1 = runningRepository.save(createRunning(member1, savedCourse1));
        running2 = runningRepository.save(createRunning(member1, savedCourse1));
        running3 = runningRepository.save(createRunning(member1, savedCourse2));
        running4 = runningRepository.save(createRunning(member2, savedCourse2));
    }


    @DisplayName("특정 코스 ID에 해당하는 모든 러닝 ID 목록을 조회한다")
    @Test
    void testSaveAndSelect() {
        // when
        Long targetCourseId = savedCourse1.getId();
        List<Long> runningIds = runningRepository.findIdsByCourseId(targetCourseId);

        // then
        Assertions.assertThat(runningIds.contains(running1.getId())).isTrue();
        Assertions.assertThat(runningIds).hasSize(2);
    }

    @DisplayName("러닝 ID와 멤버 ID로 러닝을 조회한다.")
    @Test
    void testFindByIdAndMemberId() {
        // when
        Running existRunning = runningRepository.findByIdAndMemberId(running1.getId(), member1.getId()).get();
        Optional<Running> nonExistRunning1 = runningRepository.findByIdAndMemberId(running2.getId(), member2.getId());
        Optional<Running> nonExistRunning2 = runningRepository.findByIdAndMemberId(running4.getId(), member1.getId());

        // then
        Assertions.assertThat(existRunning.getId()).isEqualTo(running1.getId());
        Assertions.assertThat(nonExistRunning1.isPresent()).isFalse();
        Assertions.assertThat(nonExistRunning2.isPresent()).isFalse();
    }

    @DisplayName("러닝 ID로 시계열 URL을 조회한다.")
    @Test
    void testFindRunningUrlByRunningId() {
        // when
        String url = runningRepository.findById(running1.getId()).get().getTelemetryUrl();

        // then
        Assertions.assertThat(url).isEqualTo(running1.getTelemetryUrl());
    }

    @DisplayName("코스에 대해서 고스트가 러닝한 ID가 있는지 확인한다.")
    @Test
    void testFindGhostRunningIdWithCourse() {
        // when
        List<Long> course1RunningIds = runningRepository.findIdsByCourseId(savedCourse1.getId());
        List<Long> course2RunningIds = runningRepository.findIdsByCourseId(savedCourse2.getId());
        List<Long> noneRunningIds = runningRepository.findIdsByCourseId(Long.MAX_VALUE);

        // then
        Assertions.assertThat(course1RunningIds).hasSize(2);
        Assertions.assertThat(course2RunningIds).hasSize(2);
        Assertions.assertThat(noneRunningIds).hasSize(0);
    }

    @DisplayName("혼자 뛴 러닝에 대한 상세 정보를 조회한다.")
    @Test
    void testFindSoloRunInfoByRunningId() {
        // given
        Course newCourse = createCourse();
        newCourse.setName("테스트 코스");
        courseRepository.save(newCourse);
        Running newRunning1 = runningRepository.save(createRunning(member1, newCourse));
        Running newRunning2 = runningRepository.save(createRunning(member1, newCourse));

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningRepository.findSoloRunInfoById(newRunning1.getId()).get();

        // then
        Assertions.assertThat(soloRunDetailInfo.getStartedAt()).isEqualTo(newRunning1.getStartedAt());
        Assertions.assertThat(soloRunDetailInfo.getRunningName()).isEqualTo(newRunning1.getRunningName());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(newRunning1.getCourse().getId());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getName()).isEqualTo(newRunning1.getCourse().getName());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getRunnersCount()).isEqualTo(2);
        Assertions.assertThat(soloRunDetailInfo.getTelemetryUrl()).isEqualTo(newRunning1.getTelemetryUrl());
        Assertions.assertThat(soloRunDetailInfo.getRecordInfo().getDistance()).isEqualTo(newRunning1.getRunningRecord().getDistance());
        Assertions.assertThat(soloRunDetailInfo.getRecordInfo().getDuration()).isEqualTo(newRunning1.getRunningRecord().getDuration());
    }

    @DisplayName("혼자 뛴 러닝에 대해 코스를 공개하지 않았다면 코스 정보는 Null이 조회된다.")
    @Test
    void testFindSoloRunInfoWithNullCourseInfo() {
        // when
        SoloRunDetailInfo soloRunDetailInfo = runningRepository.findSoloRunInfoById(running1.getId()).get();

        // then
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo()).isNull();
    }

    @DisplayName("혼자 뛴 러닝에 대해 상세 정보를 조회할 때 없다면 Null이 뜬다.")
    @Test
    void testFindSoloRunInfoByRunningIdNull() {
        // then
        Assertions.assertThat(runningRepository.findSoloRunInfoById(Long.MAX_VALUE)).isEmpty();
    }

    @DisplayName("나의 닉네임, 프로필 URL, 러닝, 코스의 상세정보를 조회한다.")
    @Test
    void testFindGhostRunInfoByRunningId() {
        // given
        Course newCourse = createCourse();
        newCourse.setName("테스트 코스");
        courseRepository.save(newCourse);
        Running newRunning1 = runningRepository.save(createRunning(member1, newCourse));

        // when
        GhostRunDetailInfo ghostRunDetailInfo = runningRepository.findGhostRunInfoById(newRunning1.getId()).get();

        // then
        Assertions.assertThat(ghostRunDetailInfo.getStartedAt()).isEqualTo(newRunning1.getStartedAt());
        Assertions.assertThat(ghostRunDetailInfo.getRunningName()).isEqualTo(newRunning1.getRunningName());
        Assertions.assertThat(ghostRunDetailInfo.getCourseInfo().getName()).isEqualTo("테스트 코스");
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getNickname()).isEqualTo("멤버1");
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getProfileUrl()).isEqualTo("프로필 URL");
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDistance()).isEqualTo(newRunning1.getRunningRecord().getDistance());
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDuration()).isEqualTo(newRunning1.getRunningRecord().getDuration());
        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo()).isNull();
        Assertions.assertThat(ghostRunDetailInfo.getTelemetryUrl()).isEqualTo(newRunning1.getTelemetryUrl());
    }

    @DisplayName("나의 닉네임, 프로필 URL, 러닝 상세정보를 조회한다.")
    @Test
    void testFindMemberAndRunRecordInfoById() {
        // when
        MemberAndRunRecordInfo memberAndRunRecordInfo = runningRepository.findMemberAndRunRecordInfoById(running1.getId()).get();

        // then
        Assertions.assertThat(memberAndRunRecordInfo.getNickname()).isEqualTo("멤버1");
        Assertions.assertThat(memberAndRunRecordInfo.getProfileUrl()).isEqualTo("프로필 URL");
        Assertions.assertThat(memberAndRunRecordInfo.getRecordInfo().getCadence()).isEqualTo(running1.getRunningRecord().getCadence());
        Assertions.assertThat(memberAndRunRecordInfo.getRecordInfo().getDuration()).isEqualTo(running1.getRunningRecord().getDuration());
        Assertions.assertThat(memberAndRunRecordInfo.getRecordInfo().getAveragePace()).isEqualTo(running1.getRunningRecord().getAveragePace());
    }

    @DisplayName("나와 고스트의 닉네임, 프로필 URL, 러닝 상세정보를 조회한다.")
    @Test
    void testGhostRunInfoAndComparison() {
        // given
        Course myCourse = createCourse();
        myCourse.setName("테스트 코스");
        courseRepository.save(myCourse);
        Running myRunning = runningRepository.save(createMyRunning(member2, myCourse));

        // when
        GhostRunDetailInfo ghostRunDetailInfo = runningRepository.findGhostRunInfoById(myRunning.getId()).get();
        MemberAndRunRecordInfo memberAndRunRecordInfo = runningRepository.findMemberAndRunRecordInfoById(running1.getId()).get();
        ghostRunDetailInfo.setGhostRunInfo(memberAndRunRecordInfo);

        // then
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getNickname()).isEqualTo("멤버2");
        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo().getNickname()).isEqualTo("멤버1");
        Assertions.assertThat(ghostRunDetailInfo.getComparisonInfo().getDuration()).isEqualTo(0L);
        Assertions.assertThat(ghostRunDetailInfo.getComparisonInfo().getPace()).isEqualTo(-1.0);
        Assertions.assertThat(ghostRunDetailInfo.getGhostRunId()).isEqualTo(myRunning.getGhostRunningId());
    }

    @DisplayName("러닝 시계열 url을 조회한다.")
    @Test
    void testGetRunningTelemetriesUrl() {
        // when
        String url = runningRepository.findTelemetryUrlById(running1.getId()).get();

        // then
        Assertions.assertThat(url).isEqualTo(running1.getTelemetryUrl());
    }

    private Running createMyRunning(Member testMember, Course testCourse ) {
        RunningRecord testRunningRecord = RunningRecord.of(6.2, 40, -20, 5.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    private Running createRunning(Member testMember, Course testCourse ) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse() {
        CourseProfile testCourseProfile = CourseProfile.of(5.2, 40, -10);
        StartPoint testStartPoint = StartPoint.fromCoordinates(37.545354, 34.7878);
        return Course.of(testCourseProfile, testStartPoint, "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }
}
