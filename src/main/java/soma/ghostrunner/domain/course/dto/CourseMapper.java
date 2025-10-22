package soma.ghostrunner.domain.course.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.query.CourseQueryModel;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;

@Mapper(componentModel = "spring", uses = { CourseSubMapper.class})
public interface CourseMapper {

    @Mapping(source = "member.uuid", target = "ownerUuid")
    @Mapping(source = "startCoordinate.latitude", target = "startLat")
    @Mapping(source = "startCoordinate.longitude", target = "startLng")
    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
    @Mapping(source = "courseDataUrls.routeUrl", target = "routeUrl")
    @Mapping(source = "courseDataUrls.checkpointsUrl", target = "checkpointsUrl")
    @Mapping(source = "courseDataUrls.thumbnailUrl", target = "thumbnailUrl")
    @Mapping(source = "courseProfile.elevationAverage", target = "elevationAverage")
    @Mapping(source = "courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "courseProfile.elevationLoss", target = "elevationLoss")
    CoursePreviewDto toCoursePreviewDto(Course course);

    @Mapping(source = "runners", target = "runners")
    CourseMapResponse toCourseMapResponse(CoursePreviewDto courseDto, List<RunnerProfile> runners,
                                          long runnersCount, CourseGhostResponse myGhostInfo);

    @Mapping(source = "runners", target = "runners")
    CourseMapResponse toCourseMapResponseTmp(CoursePreviewDto courseDto, List<RunnerProfile> runners,
                                          long runnersCount, Boolean hasRan);

    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
    @Mapping(source = "course.courseDataUrls.checkpointsUrl", target = "checkpointsUrl")
    @Mapping(source = "course.courseProfile.elevationAverage", target = "elevationAverage")
    @Mapping(source = "course.courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "course.courseProfile.elevationLoss", target = "elevationLoss")
    @Mapping(source = "course.createdAt", target = "createdAt")
    @Mapping(source = "courseStats.avgCompletionTime", target = "averageCompletionTime")
    @Mapping(source = "courseStats.avgFinisherPace", target = "averageFinisherPace")
    @Mapping(source = "courseStats.avgFinisherCadence", target = "averageFinisherCadence")
    @Mapping(source = "courseStats.avgCaloriesBurned", target = "averageCaloriesBurned")
    @Mapping(source = "courseStats.lowestFinisherPace", target = "lowestFinisherPace")
    @Mapping(source = "userStats.lowestPace", target = "myLowestPace")
    @Mapping(source = "userStats.avgPace", target = "myAveragePace")
    @Mapping(source = "userStats.highestPace", target = "myHighestPace")
    @Mapping(source = "ghostStats", target = "myGhostInfo")
    CourseDetailedResponse toCourseDetailedResponse(Course course, String telemetryUrl, CourseRunStatisticsDto courseStats,
                                                    UserPaceStatsDto userStats, CourseGhostResponse ghostStats);

    @Mapping(source = "running.member.uuid", target = "runnerUuid")
    @Mapping(source = "running.member.profilePictureUrl", target = "runnerProfileUrl")
    @Mapping(source = "running.member.nickname", target = "runnerNickname")
    @Mapping(source = "running.id", target = "runningId")
    @Mapping(source = "running.runningRecord.duration", target = "duration")
    @Mapping(source = "running.runningRecord.bpm", target = "bpm")
    @Mapping(source = "running.runningRecord.cadence", target = "cadence")
    @Mapping(source = "running.runningRecord.averagePace", target = "averagePace")
    @Mapping(source = "running.createdAt", target = "startedAt")
    CourseRankingResponse toRankingResponse(Running running, Integer rank);

    @Mapping(source = "course.id", target = "courseId")
    @Mapping(source = "course.name", target = "courseName")
    @Mapping(source = "course.courseDataUrls.thumbnailUrl", target = "courseThumbnailUrl")
    @Mapping(source = "course.startCoordinate.latitude", target = "startLat")
    @Mapping(source = "course.startCoordinate.longitude", target = "startLng")
    @Mapping(source = "course.courseProfile.elevationGain", target = "elevationGain")
    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
    @Mapping(source = "course.isPublic", target = "courseIsPublic")
    @Mapping(source = "course.createdAt", target = "courseCreatedAt")
    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.uuid", target = "memberUuid")
    @Mapping(source = "member.nickname", target = "memberNickname")
    @Mapping(source = "member.profilePictureUrl", target = "memberProfileImageUrl")
    CourseWithMemberDetailsDto toCourseWithMemberDetailsDto(Course course, Member member);

    @Mapping(source = "courseDto.courseId", target = "id")
    @Mapping(source = "courseDto.courseName", target = "name")
    @Mapping(source = "courseDto.courseThumbnailUrl", target = "thumbnailUrl")
    @Mapping(source = "courseDto.elevationGain", target = "elevationGain")
    @Mapping(source = "courseDto.distance", target = "distance")
    @Mapping(source = "courseDto.courseIsPublic", target = "isPublic")
    @Mapping(source = "courseDto.courseCreatedAt", target = "createdAt")
    @Mapping(source = "ghostStats", target = "myGhostInfo")
    CourseSummaryResponse toCourseSummaryResponse(CourseWithMemberDetailsDto courseDto, Integer uniqueRunnersCount,
                                                  Integer totalRunsCount, Double averageCompletionTime,
                                                  Double averageFinisherPace, Double averageFinisherCadence,
                                                  CourseGhostResponse ghostStats);

    @Mapping(source = "avgCompletionTime", target = "averageCompletionTime")
    @Mapping(source = "avgFinisherPace", target = "averageFinisherPace")
    @Mapping(source = "avgFinisherCadence", target = "averageFinisherCadence")
    @Mapping(source = "avgCaloriesBurned", target = "averageCaloriesBurned")
    CourseStatisticsResponse toCourseStatisticsResponse(CourseRunStatisticsDto stats);

    @Mapping(source = "ghosts", target = "topRunners")
    CourseQueryModel toCourseQueryModel(CoursePreviewDto courseDto, List<CourseGhostResponse> ghosts, long runnerCount);


}

@Mapper(componentModel = "spring")
interface CourseSubMapper {
    @Mapping(source = "ghost.runnerUuid", target = "uuid")
    @Mapping(source = "ghost.runnerProfileUrl", target = "profileUrl")
    RunnerProfile toMemberRecordDto(CourseGhostResponse ghost);
}
