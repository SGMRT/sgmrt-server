package soma.ghostrunner.domain.course.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.query.CourseQueryModel;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseMapper 단위 테스트")
class CourseMapperTest {

    private CourseMapper courseMapper;
    private CourseSubMapper courseSubMapper;

    private final LocalDateTime FAKE_NOW = LocalDateTime.of(2025, 9, 1, 10, 0, 0);

    @BeforeEach
    void setUp() {
        courseMapper = new CourseMapperImpl();
        courseSubMapper = Mappers.getMapper(CourseSubMapper.class);
        // courseMapper에 courseSubMapper 주입
        ReflectionTestUtils.setField(courseMapper, "courseSubMapper", courseSubMapper);
    }

    @DisplayName("Course 엔티티를 CoursePreviewDto로 변환한다.")
    @Test
    void toCoursePreviewDto() {
        // given
        Member member = createMember();
        Course course = createCourse(member);

        // when
        CoursePreviewDto dto = courseMapper.toCoursePreviewDto(course);

        // then
        assertThat(dto.id()).isEqualTo(course.getId());
        assertThat(dto.name()).isEqualTo(course.getName());
        assertThat(dto.ownerUuid()).isEqualTo(course.getMember().getUuid());
        assertThat(dto.source()).isEqualTo(course.getSource());
        assertThat(dto.startLat()).isEqualTo(course.getStartCoordinate().getLatitude());
        assertThat(dto.startLng()).isEqualTo(course.getStartCoordinate().getLongitude());
        assertThat(dto.routeUrl()).isEqualTo(course.getCourseDataUrls().getRouteUrl());
        assertThat(dto.checkpointsUrl()).isEqualTo(course.getCourseDataUrls().getCheckpointsUrl());
        assertThat(dto.thumbnailUrl()).isEqualTo(course.getCourseDataUrls().getThumbnailUrl());
        assertThat(dto.distance()).isEqualTo((int) (course.getCourseProfile().getDistance() * 1000));
        assertThat(dto.elevationAverage()).isEqualTo(course.getCourseProfile().getElevationAverage().intValue());
        assertThat(dto.elevationGain()).isEqualTo(course.getCourseProfile().getElevationGain().intValue());
        assertThat(dto.elevationLoss()).isEqualTo(course.getCourseProfile().getElevationLoss().intValue());
        assertThat(dto.createdAt()).isEqualTo(course.getCreatedAt());
    }

    @DisplayName("CoursePreviewDto와 CourseGhostResponse 리스트를 CourseMapResponse로 변환한다.")
    @Test
    void toCourseMapResponse() {
        // given
        CoursePreviewDto courseDto = createCoursePreviewDto();
        List<RunnerProfile> ghosts = List.of(createRunnerProfile("uuid-1"), createRunnerProfile("uuid-2"));
        CourseGhostResponse myGhostInfo = createCourseGhostResponse();
        long runnersCount = 2L;

        // when
        CourseMapResponse response = courseMapper.toCourseMapResponse(courseDto, ghosts, runnersCount, myGhostInfo);

        // then
        // CoursePreviewDto 필드 검증
        assertThat(response.id()).isEqualTo(courseDto.id());
        assertThat(response.name()).isEqualTo(courseDto.name());
        assertThat(response.ownerUuid()).isEqualTo(courseDto.ownerUuid());
        assertThat(response.source()).isEqualTo(courseDto.source());
        assertThat(response.startLat()).isEqualTo(courseDto.startLat());
        assertThat(response.startLng()).isEqualTo(courseDto.startLng());
        assertThat(response.routeUrl()).isEqualTo(courseDto.routeUrl());
        assertThat(response.checkpointsUrl()).isEqualTo(courseDto.checkpointsUrl());
        assertThat(response.thumbnailUrl()).isEqualTo(courseDto.thumbnailUrl());
        assertThat(response.distance()).isEqualTo(courseDto.distance());
        assertThat(response.elevationAverage()).isEqualTo(courseDto.elevationAverage());
        assertThat(response.elevationGain()).isEqualTo(courseDto.elevationGain());
        assertThat(response.elevationLoss()).isEqualTo(courseDto.elevationLoss());
        assertThat(response.createdAt()).isEqualTo(courseDto.createdAt());
        // CourseMapResponse 필드 검증
        assertThat(response.runnersCount()).isEqualTo(runnersCount);
        assertThat(response.runners()).hasSize(2);
        for(int i = 0; i < ghosts.size(); i++) {
            assertThat(response.runners().get(i).uuid()).isEqualTo(ghosts.get(i).uuid());
            assertThat(response.runners().get(i).profileUrl()).isEqualTo(ghosts.get(i).profileUrl());
        }
        assertThat(response.myGhostInfo()).isEqualTo(myGhostInfo);
    }

    @DisplayName("Course와 통계 데이터를 CourseDetailedResponse로 변환한다.")
    @Test
    void toCourseDetailedResponse() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        String telemetryUrl = "http://example.com/telemetry";
        CourseRunStatisticsDto courseStats = createCourseRunStatisticsDto();
        UserPaceStatsDto userStats = createUserPaceStatsDto();
        CourseGhostResponse ghostStats = createCourseGhostResponse();

        // when
        CourseDetailedResponse response = courseMapper.toCourseDetailedResponse(course, telemetryUrl, courseStats, userStats, ghostStats);

        // then
        // Course 필드 검증
        assertThat(response.id()).isEqualTo(course.getId());
        assertThat(response.name()).isEqualTo(course.getName());
        assertThat(response.source()).isEqualTo(course.getSource());
        assertThat(response.telemetryUrl()).isEqualTo(telemetryUrl);
        assertThat(response.checkpointsUrl()).isEqualTo(course.getCourseDataUrls().getCheckpointsUrl());
        assertThat(response.distance()).isEqualTo(course.getCourseProfile().getDistance().intValue() * 1000);
        assertThat(response.elevationAverage()).isEqualTo(course.getCourseProfile().getElevationAverage().intValue());
        assertThat(response.elevationGain()).isEqualTo(course.getCourseProfile().getElevationGain().intValue());
        assertThat(response.elevationLoss()).isEqualTo(course.getCourseProfile().getElevationLoss().intValue());
        assertThat(response.createdAt()).isEqualTo(course.getCreatedAt());
        // CourseRunStatisticsDto 필드 검증
        assertThat(response.averageCompletionTime()).isEqualTo(courseStats.getAvgCompletionTime().intValue());
        assertThat(response.averageFinisherPace()).isEqualTo(courseStats.getAvgFinisherPace().intValue());
        assertThat(response.averageFinisherCadence()).isEqualTo(courseStats.getAvgFinisherCadence().intValue());
        assertThat(response.lowestFinisherPace()).isEqualTo(courseStats.getLowestFinisherPace().intValue());
        assertThat(response.uniqueRunnersCount()).isEqualTo(courseStats.getUniqueRunnersCount().intValue());
        assertThat(response.totalRunsCount()).isEqualTo(courseStats.getTotalRunsCount().intValue());
        // UserPaceStatsDto 필드 검증
        assertThat(response.myLowestPace()).isEqualTo(userStats.getLowestPace());
        assertThat(response.myAveragePace()).isEqualTo(userStats.getAvgPace());
        assertThat(response.myHighestPace()).isEqualTo(userStats.getHighestPace());
        // CourseGhostResponse 필드 검증
        assertThat(response.myGhostInfo()).isEqualTo(ghostStats);
    }

    @DisplayName("Running 엔티티와 순위를 CourseRankingResponse로 변환한다.")
    @Test
    void toRankingResponse() {
        // given
        Member member = createMember();
        Course course = createCourse(member);
        Running running = createRunning(member, course);
        Integer rank = 1;

        // when
        CourseRankingResponse response = courseMapper.toRankingResponse(running, rank);

        // then
        assertThat(response.getRank()).isEqualTo(rank);
        assertThat(response.getRunnerUuid()).isEqualTo(running.getMember().getUuid());
        assertThat(response.getRunnerProfileUrl()).isEqualTo(running.getMember().getProfilePictureUrl());
        assertThat(response.getRunnerNickname()).isEqualTo(running.getMember().getNickname());
        assertThat(response.getRunningId()).isEqualTo(running.getId());
        assertThat(response.getDuration()).isEqualTo(running.getRunningRecord().getDuration());
        assertThat(response.getBpm()).isEqualTo(running.getRunningRecord().getBpm());
        assertThat(response.getCadence()).isEqualTo(running.getRunningRecord().getCadence());
        assertThat(response.getAveragePace()).isEqualTo(running.getRunningRecord().getAveragePace());
        assertThat(response.getStartedAt()).isEqualTo(running.getCreatedAt());
    }

    @DisplayName("Course와 Member 엔티티를 CourseWithMemberDetailsDto로 변환한다.")
    @Test
    void toCourseWithMemberDetailsDto() {
        // given
        Member member = createMember();
        Course course = createCourse(member);

        // when
        CourseWithMemberDetailsDto dto = courseMapper.toCourseWithMemberDetailsDto(course, member);

        // then
        // Course 필드 검증
        assertThat(dto.getCourseId()).isEqualTo(course.getId());
        assertThat(dto.getCourseName()).isEqualTo(course.getName());
        assertThat(dto.getCourseThumbnailUrl()).isEqualTo(course.getCourseDataUrls().getThumbnailUrl());
        assertThat(dto.getStartLat()).isEqualTo(course.getStartCoordinate().getLatitude());
        assertThat(dto.getStartLng()).isEqualTo(course.getStartCoordinate().getLongitude());
        assertThat(dto.getDistance()).isEqualTo(course.getCourseProfile().getDistance().intValue() * 1000);
        assertThat(dto.getCourseIsPublic()).isEqualTo(course.getIsPublic());
        assertThat(dto.getCourseCreatedAt()).isEqualTo(course.getCreatedAt());

        // Member 필드 검증
        assertThat(dto.getMemberId()).isEqualTo(member.getId());
        assertThat(dto.getMemberUuid()).isEqualTo(member.getUuid());
        assertThat(dto.getMemberNickname()).isEqualTo(member.getNickname());
        assertThat(dto.getMemberProfileImageUrl()).isEqualTo(member.getProfilePictureUrl());
    }

    @DisplayName("CourseWithMemberDetailsDto와 통계정보를 CourseSummaryResponse로 변환한다.")
    @Test
    void toCourseSummaryResponse() {
        // given
        CourseWithMemberDetailsDto courseDto = createCourseWithMemberDetailsDto();
        Integer uniqueRunnersCount = 10;
        Integer totalRunsCount = 100;
        Double avgCompletionTime = 3600.0;
        Double avgFinisherPace = 5.5;
        Double avgFinisherCadence = 180.0;
        CourseGhostResponse ghostStat = createCourseGhostResponse();

        // when
        CourseSummaryResponse response = courseMapper.toCourseSummaryResponse(courseDto, uniqueRunnersCount,
                totalRunsCount, avgCompletionTime, avgFinisherPace, avgFinisherCadence, ghostStat);

        // then
        // CourseWithMemberDetailsDto 필드 검증
        assertThat(response.id()).isEqualTo(courseDto.getCourseId());
        assertThat(response.name()).isEqualTo(courseDto.getCourseName());
        assertThat(response.elevationGain()).isEqualTo(courseDto.getElevationGain());
        assertThat(response.thumbnailUrl()).isEqualTo(courseDto.getCourseThumbnailUrl());
        assertThat(response.createdAt()).isEqualTo(courseDto.getCourseCreatedAt());
        assertThat(response.distance()).isEqualTo(courseDto.getDistance());
        assertThat(response.isPublic()).isEqualTo(courseDto.getCourseIsPublic());

        // 파라미터 매핑 검증
        assertThat(response.uniqueRunnersCount()).isEqualTo(uniqueRunnersCount);
        assertThat(response.totalRunsCount()).isEqualTo(totalRunsCount);
        assertThat(response.averageCompletionTime()).isEqualTo(avgCompletionTime.intValue());
        assertThat(response.averageFinisherPace()).isEqualTo(avgFinisherPace);
        assertThat(response.averageFinisherCadence()).isEqualTo(avgFinisherCadence.intValue());

        // ghostStat 필드 검증
        assertThat(response.myGhostInfo()).isEqualTo(ghostStat);
    }

    @DisplayName("CourseRunStatisticsDto를 CourseStatisticsResponse로 변환한다.")
    @Test
    void toCourseStatisticsResponse() {
        // given
        CourseRunStatisticsDto stats = createCourseRunStatisticsDto();

        // when
        CourseStatisticsResponse response = courseMapper.toCourseStatisticsResponse(stats);

        // then
        assertThat(response.averageCompletionTime()).isEqualTo(stats.getAvgCompletionTime());
        assertThat(response.averageFinisherPace()).isEqualTo(stats.getAvgFinisherPace());
        assertThat(response.averageFinisherCadence()).isEqualTo(stats.getAvgFinisherCadence());
        assertThat(response.averageCaloriesBurned()).isEqualTo(stats.getAvgCaloriesBurned());
        assertThat(response.lowestFinisherPace()).isEqualTo(stats.getLowestFinisherPace());
        assertThat(response.uniqueRunnersCount()).isEqualTo(stats.getUniqueRunnersCount());
        assertThat(response.totalRunsCount()).isEqualTo(stats.getTotalRunsCount());
    }

    @DisplayName("(SubMapper) CourseGhostResponse를 MemberRecord로 변환한다.")
    @Test
    void toMemberRecordDto() {
        // given
        CourseGhostResponse ghost = createCourseGhostResponse();

        // when
        RunnerProfile memberRecord = courseSubMapper.toMemberRecordDto(ghost);

        // then
        assertThat(memberRecord.uuid()).isEqualTo(ghost.runnerUuid());
        assertThat(memberRecord.profileUrl()).isEqualTo(ghost.runnerProfileUrl());
    }

    @DisplayName("CoursePreviewDto와 CourseGhostResponse 리스트를 CourseQueryModel로 변환한다.")
    @Test
    void toCourseQueryModel() {
        // given
        CoursePreviewDto courseDto = createCoursePreviewDto();
        List<CourseGhostResponse> ghosts = List.of(createCourseGhostResponse(), createCourseGhostResponse());
        long runnerCount = 2L;

        // when
        CourseQueryModel queryModel = courseMapper.toCourseQueryModel(courseDto, ghosts, runnerCount);

        // then
        assertThat(queryModel.id()).isEqualTo(courseDto.id());
        assertThat(queryModel.name()).isEqualTo(courseDto.name());
        assertThat(queryModel.runnerCount()).isEqualTo((int) runnerCount);
        assertThat(queryModel.topRunners()).hasSize(ghosts.size());
        for (int i = 0; i < ghosts.size(); i++) {
            assertThat(queryModel.topRunners().get(i).uuid()).isEqualTo(ghosts.get(i).runnerUuid());
            assertThat(queryModel.topRunners().get(i).profileUrl()).isEqualTo(ghosts.get(i).runnerProfileUrl());
        }

    }


    // --- 헬퍼 메소드 ---

    private Member createMember() {
        Member member = Member.of("testUser", "http://profile.url");
        member.setUuid("test-uuid-1234");
        return member;
    }

    private Course createCourse(Member member) {
        CourseProfile profile = CourseProfile.of(5.0, 10.0, 100.0, 50.0);
        Coordinate coordinate = Coordinate.of(37d, 129d);
        CourseDataUrls urls = CourseDataUrls.of("route.url", "checkpoint.url", "thumbnail.url");

        return Course.of(member, "Test Course", profile, coordinate, CourseSource.USER, true, urls);
    }

    private Running createRunning(Member member, Course course) {
        RunningRecord record = RunningRecord.of(5.0, 6.0, 10.0, 10.0,
                        180.0, 200.0, 150.0, 600L,
                        180, 180, 180);

        return Running.of("Test Run", null, null, record,
                System.currentTimeMillis(), true, false, "raw-data.url",
                "interpolated-data.url", "screenshot.url", member, course);
    }

    private CoursePreviewDto createCoursePreviewDto() {
        return new CoursePreviewDto(1L, "Test Course", "dummy-uuid", 37.0, 127.0, CourseSource.USER, "route.url", "checkpoint.url", "thumbnail.url", 5000, 10, 100, -50, LocalDateTime.now());
    }

    private RunnerProfile createRunnerProfile(String uuid) {
        return new RunnerProfile(uuid, "profile.url");
    }

    private CourseGhostResponse createCourseGhostResponse() {
        return new CourseGhostResponse("runner-uuid", "profile.url", "runner-nickname", 100L, "run-name", 6.0, 180, 160, 1800L, true, LocalDateTime.now());
    }

    private CourseRunStatisticsDto createCourseRunStatisticsDto() {
        return new CourseRunStatisticsDto(1800.0, 6.0, 180.0, 300.0, 5.0, 10, 100);
    }

    private UserPaceStatsDto createUserPaceStatsDto() {
        return new UserPaceStatsDto("user-uuid", 5.5, 6.0, 6.5);
    }

    private CourseWithMemberDetailsDto createCourseWithMemberDetailsDto() {
        return new CourseWithMemberDetailsDto(1L, "Course Name", "thumb.url", 37.0, 127.0, 5000, 10, -10, true, LocalDateTime.now(), 1L, "member-uuid", "nickname", "profile.url");
    }
}
