package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.infra.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

// TODO : 단위 테스트로 전환
class RunningQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningQueryService runningQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    RunningRepository runningRepository;

    @MockitoBean
    RunningTelemetryQueryService runningTelemetryQueryService;

    @DisplayName("혼자 뛴 러닝에 대한 상세 정보를 조회한다.")
    @Test
    void findSoloRunInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createPublicCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course, "MockInterpolatedTelemetrySavedUrl");
        runningRepository.save(running);

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningQueryService.findSoloRunInfo(running.getId(), member.getUuid());

        // then
        assertThat(soloRunDetailInfo.getStartedAt()).isEqualTo(running.getStartedAt());
        assertThat(soloRunDetailInfo.getRunningName()).isEqualTo(running.getRunningName());
        assertThat(soloRunDetailInfo.getTelemetryUrl()).isEqualTo(running.getRunningDataUrls().getInterpolatedTelemetryUrl());
        assertThat(soloRunDetailInfo.getRecordInfo().getDistance()).isEqualTo(running.getRunningRecord().getDistance());
        assertThat(soloRunDetailInfo.getRecordInfo().getDuration()).isEqualTo(running.getRunningRecord().getDuration());

        assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(running.getCourse().getId());
        assertThat(soloRunDetailInfo.getCourseInfo().getName()).isEqualTo(running.getCourse().getName());
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createPublicCourse(Member testMember, String courseName) {
        CourseProfile testCourseProfile = createPublicCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "Mock URL", "Mock URL", "Mock URL");
        course.setName(courseName);
        course.setIsPublic(true);
        return course;
    }

    private Running createSoloRunning(Member testMember, Course testCourse, String interpolatedTelemetrySavedUrl) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO,
                null, testRunningRecord,
                1750729987181L, true, false,
                "URL", interpolatedTelemetrySavedUrl, "URL",
                testMember, testCourse);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    private List<TelemetryDto> createTelemetryDtos() {
        return List.of(
                new TelemetryDto(0L, 37.2, 37.5, 110.0, 6.0, 100.0, 120, 110, true),
                new TelemetryDto(1L, 37.3, 37.6, 110.1, 6.1, 101.0, 121, 111, true),
                new TelemetryDto(2L, 37.4, 37.7, 110.2, 6.2, 102.0, 122, 112, true),
                new TelemetryDto(3L, 37.5, 37.8, 110.3, 6.3, 103.0, 123, 113, false)
        );
    }

    @DisplayName("혼자 뛴 러닝에 대한 상세 정보를 조회할 때 공개되지 않은 코스 정보라도 CourseInfo는 모두 조회된다.")
    @Test
    void findSoloRunInUnPublicCourseInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createPrivateCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course, "MockInterpolatedTelemetrySavedUrl");
        runningRepository.save(running);

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningQueryService.findSoloRunInfo(running.getId(), member.getUuid());

        // then
        assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(course.getId());
        assertThat(soloRunDetailInfo.getCourseInfo().getIsPublic()).isFalse();
    }

    private Course createPrivateCourse(Member testMember, String courseName) {
        CourseProfile testCourseProfile = createPublicCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "Mock URL", "Mock URL", "Mock URL");
        course.setName(courseName);
        return course;
    }

    @DisplayName("혼자 뛴 러닝을 조회할 때 자신의 러닝 정보가 아니거나 없는 데이터라면 NOT_FOUND 예외를 응답한다.")
    @Test
    void findSoloRunInfoByNoneOwnerId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createPublicCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course, "MockInterpolatedTelemetrySavedUrl");
        runningRepository.save(running);

        // when // then
        assertThatThrownBy(
                        () -> runningQueryService.findSoloRunInfo(running.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("id " + running.getId() +" is not found");
    }

    @DisplayName("고스트와 뛴 러닝에 대한 상세 정보를 조회한다.")
    @Test
    void findGhostRunInfo() {
        // given
        Member member = createMember("이복둥");
        Member followingMember = createMember("고스트 이복둥");
        memberRepository.saveAll(List.of(member, followingMember));

        Course course = createPublicCourse(member, "테스트 코스");
        courseRepository.save(course);

        RunningRecord runningRecord = RunningRecord.of(4.0, 30.0, 40.0, -20.0, 6.1,
                6.1, 8.1, 120L, 50, 100, 120);
        Running running = Running.of("러닝 제목", RunningMode.SOLO, null, runningRecord,
                1750729987181L, true, false, "URL", "URL", "URL", member, course);
        runningRepository.save(running);

        RunningRecord ghostRunningRecord = RunningRecord.of(5.0, 50.0, 30.0, -10.0, 7.1,
                7.1, 9.1, 130L, 60, 110, 130);
        Running followingRunning = Running.of("고스트 러닝 제목", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                1750729987181L, true, false, "URL", "URL", "URL", followingMember, course);
        Running followingRunning2 = Running.of("고스트 러닝 제목2", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                1750729987181L, true, false, "URL", "URL", "URL", followingMember, course);
        runningRepository.saveAll(List.of(followingRunning, followingRunning2));

        // when
        GhostRunDetailInfo ghostRunDetailInfo = runningQueryService.findGhostRunInfo(
                followingRunning.getId(), running.getId(), followingMember.getUuid());

        // then
        assertThat(ghostRunDetailInfo.getStartedAt()).isEqualTo(followingRunning.getStartedAt());
        assertThat(ghostRunDetailInfo.getRunningName()).isEqualTo(followingRunning.getRunningName());
        assertThat(ghostRunDetailInfo.getTelemetryUrl()).isEqualTo(followingRunning.getRunningDataUrls().getInterpolatedTelemetryUrl());

        assertThat(ghostRunDetailInfo.getCourseInfo().getId()).isEqualTo(course.getId());
        assertThat(ghostRunDetailInfo.getCourseInfo().getName()).isEqualTo(course.getName());

        assertThat(ghostRunDetailInfo.getMyRunInfo().getNickname())
                .isEqualTo(followingMember.getNickname());
        assertThat(ghostRunDetailInfo.getMyRunInfo().getProfileUrl())
                .isEqualTo(followingMember.getProfilePictureUrl());
        assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDistance())
                .isEqualTo(followingRunning.getRunningRecord().getDistance());
        assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDuration())
                .isEqualTo(followingRunning.getRunningRecord().getDuration());

        assertThat(ghostRunDetailInfo.getGhostRunId()).isEqualTo(running.getId());

        assertThat(ghostRunDetailInfo.getGhostRunInfo().getNickname())
                .isEqualTo(member.getNickname());
        assertThat(ghostRunDetailInfo.getGhostRunInfo().getProfileUrl())
                .isEqualTo(member.getProfilePictureUrl());
        assertThat(ghostRunDetailInfo.getGhostRunInfo().getRecordInfo().getCadence())
                .isEqualTo(running.getRunningRecord().getCadence());
        assertThat(ghostRunDetailInfo.getGhostRunInfo().getRecordInfo().getDuration())
                .isEqualTo(running.getRunningRecord().getDuration());

        assertThat(ghostRunDetailInfo.getComparisonInfo().getDistance()).isEqualTo(-0.8);
        assertThat(ghostRunDetailInfo.getComparisonInfo().getDuration()).isEqualTo(10L);
        assertThat(ghostRunDetailInfo.getComparisonInfo().getCadence()).isEqualTo(10);
        assertThat(ghostRunDetailInfo.getComparisonInfo().getPace()).isEqualTo(1.0);
    }

    @DisplayName("고스트와 뛴 러닝에 대한 상세 정보를 조회할 때 입력한 고스트 러닝 ID가 실제 고스트 러닝 ID와 일치하지 않을 때 예외가 발생한다.")
    @Test
    void findGhostRunInfoWithInvalidGhostRunningId() {
        // given
        Member member = createMember("이복둥");
        Member followingMember = createMember("고스트 이복둥");
        memberRepository.saveAll(List.of(member, followingMember));

        Course course = createPublicCourse(member, "테스트 코스");
        courseRepository.save(course);

        RunningRecord runningRecord = RunningRecord.of(4.0, 40.0, 60.0, -30.0,
                6.1, 6.1, 8.1, 120L, 50, 100, 120);
        Running running = Running.of("러닝 제목", RunningMode.SOLO, null, runningRecord,
                1750729987181L, true, false, "URL", "URL", "URL", member, course);
        runningRepository.save(running);

        RunningRecord ghostRunningRecord = RunningRecord.of(5.0, 50.0, 30.0, -10.0,
                7.1, 7.1, 9.1, 130L, 60, 110, 130);
        Running followingRunning = Running.of("고스트 러닝 제목", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                1750729987181L, true, false, "URL", "URL", "URL", followingMember, course);
        runningRepository.save(followingRunning);

        // when // then
        assertThatThrownBy(() ->
                        runningQueryService.findGhostRunInfo(
                                followingRunning.getId(), running.getId() + 1L, followingMember.getUuid()))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("고스트의 러닝 ID가 Null이거나 실제로 뛴 고스트러닝 ID가 아닌 경우");
    }

     @DisplayName("고스트와 뛴 러닝을 조회할 때 자신의 러닝 정보가 아니라면 NOT_FOUND 예외를 응답한다.")
     @Test
     void findGhostRunInfoByNoneOwnerId() {
         // given
         Member member = createMember("이복둥");
         Member followingMember = createMember("고스트 이복둥");
         memberRepository.saveAll(List.of(member, followingMember));

         Course course = createPublicCourse(member, "테스트 코스");
         courseRepository.save(course);

         RunningRecord runningRecord = RunningRecord.of(4.0, 30.0, 40.0, -20.0,
                 6.1, 6.1, 8.1, 120L, 50, 100, 120);
         Running running = Running.of("러닝 제목", RunningMode.SOLO, null, runningRecord,
                 1750729987181L, true, false, "URL", "URL", "URL", member, course);
         runningRepository.save(running);

         RunningRecord ghostRunningRecord = RunningRecord.of(5.0, 50.0, 30.0, -10.0,
                 7.1, 7.1, 9.1, 130L, 60, 110, 130);
         Running followingRunning = Running.of("고스트 러닝 제목", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                 1750729987181L, true, false, "URL", "URL", "URL", followingMember, course);
         runningRepository.save(followingRunning);

         // when // then
         assertThatThrownBy(() ->
                         runningQueryService.findGhostRunInfo(
                                 followingRunning.getId(), running.getId(), UUID.randomUUID().toString()))
                 .isInstanceOf(RunningNotFoundException.class)
                 .hasMessage("id " + followingRunning.getId() + " is not found");
     }

    @DisplayName("러닝의 전체 시계열을 조회한다.")
    @Test
    void findRunningTelemetries() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createPublicCourse(member);
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "Interpolated Telemetry Mock URL");
        runningRepository.save(running);

        // when
        String interpolatedTelemetryUrl = runningQueryService.findRunningTelemetries(running.getId(), member.getUuid());

        // then
        assertThat(interpolatedTelemetryUrl).isEqualTo("Interpolated Telemetry Mock URL");
    }

    private Running createRunning(String runningName, Course course, Member member, String interpolatedMockUrl) {
        return Running.of(
                runningName, RunningMode.SOLO, null,
                createRunningRecord(), 1750729987181L,
                true, false,
                "Raw Telemetry Mock URL", interpolatedMockUrl, "screenShot",
                member, course
        );
    }

    private Course createPublicCourse(Member testMember) {
        CourseProfile testCourseProfile = createPublicCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "Mock URL", "Mock URL", "Mock URL");
        course.setIsPublic(true);
        return course;
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createPublicCourseProfile() {
        return CourseProfile.of(5.2, 30.0, 40.0, -20.0);
    }

    @DisplayName("러닝의 전체 시계열을 조회할 때 자신의 러닝 데이터가 아니라면 예외를 응답한다.")
    @Test
    void findNonAuthorizedRunningTelemetries() {
        // given
        Member owner = createMember("이복둥");
        memberRepository.save(owner);
        Member other = createMember("타인은 지옥이다.");
        memberRepository.save(other);

        Course course = createPublicCourse(owner);
        courseRepository.save(course);

        Running running = createRunning("러닝", course, owner, "Interpolated Telemetry Mock URL");
        runningRepository.save(running);

        // when // then
        assertThatThrownBy(() -> runningQueryService.findRunningTelemetries(running.getId(), other.getUuid()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");
    }

    @DisplayName("러닝 ID로 러닝을 조회한다.")
    @Test
    void findRunningByRunningId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createPublicCourse(member);
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "러닝의 URL");
        runningRepository.save(running);

        // when
        Running savedRunning = runningQueryService.findRunningByRunningId(running.getId());

        // then
        assertThat(savedRunning.getRunningName()).isEqualTo(running.getRunningName());
        assertThat(savedRunning.getGhostRunningId()).isEqualTo(running.getGhostRunningId());
        assertThat(savedRunning.getRunningDataUrls().getInterpolatedTelemetryUrl())
                .isEqualTo(running.getRunningDataUrls().getInterpolatedTelemetryUrl());
    }

    @DisplayName("존재하지 않는 러닝 ID로 러닝을 조회하면 NOT_FOUND 예외가 발생한다.")
    @Test
    void findRunningByNoneRunningId() {
        // when // then
        assertThatThrownBy(() -> runningQueryService.findRunningByRunningId(1L))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("id " + 1L +" is not found");
    }

    @DisplayName("코스 ID를 기반으로 코스에 대한 첫 번째 러닝 데이터를 조회한다.")
    @Test
    void findFirstRunning() {
        // given
        Member member1 = createMember("이복둥1");
        Member member2 = createMember("이복둥2");
        memberRepository.saveAll(List.of(member1, member2));

        Course course = createPublicCourse(member1);
        courseRepository.save(course);

        Running running1 = createRunning("러닝1", course, member1, "러닝1의 URL");
        Running running2 = createRunning("러닝2", course, member1, "러닝2의 URL");
        Running running3 = createRunning("러닝3", course, member2, "러닝3의 URL");
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        Running firstRunning = runningQueryService.findFirstRunning(course.getId());

        // then
        assertThat(firstRunning.getRunningName()).isEqualTo(running1.getRunningName());
    }

    @DisplayName("코스 ID를 기반으로 코스에 대한 첫 번째 러닝 데이터를 조회할 때 존재하지 않는다면 NOT_FOUND를 응답한다.")
    @Test
    void findFirstRunningOnNoneCourse() {
        // given
        Member member1 = createMember("이복둥1");
        Member member2 = createMember("이복둥2");
        memberRepository.saveAll(List.of(member1, member2));

        Course course = createPublicCourse(member1);
        courseRepository.save(course);

        // when // then
        assertThatThrownBy(() -> runningQueryService.findFirstRunning(course.getId()))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("코스 ID : " + course.getId() + "에 대한 러닝 데이터가 없습니다.");
    }
  
}
