package soma.ghostrunner.domain.course.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.application.support.CoordinateConverter;

import java.util.List;

@Mapper(componentModel = "spring", uses = {CoordinateConverter.class, CourseSubMapper.class})
public interface CourseMapper {
    @Mapping(source = "startCoordinate.latitude", target = "startLat")
    @Mapping(source = "startCoordinate.longitude", target = "startLng")
    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
    @Mapping(source = "courseProfile.elevationAverage", target = "elevationAverage")
    @Mapping(source = "courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "courseProfile.elevationLoss", target = "elevationLoss")
    CourseWithCoordinatesDto toCourseWithCoordinateDto(Course course);

    @Mapping(source = "ghosts", target = "runners")
    CourseMapResponse toCourseMapResponse(CourseWithCoordinatesDto courseDto, List<CourseGhostResponse> ghosts,
                                          long runnersCount);

    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
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
    CourseDetailedResponse toCourseDetailedResponse(Course course, String telemetryUrl,
                                                    CourseRunStatisticsDto courseStats, UserPaceStatsDto userStats);

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

    CourseCoordinatesResponse toCoordinatesResponse(Course course, List<CoordinateDto> coordinates);

    @Mapping(source = "course.id", target = "courseId")
    @Mapping(source = "course.name", target = "courseName")
    @Mapping(source = "course.startCoordinate.latitude", target = "startLat")
    @Mapping(source = "course.startCoordinate.longitude", target = "startLng")
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
    @Mapping(source = "courseDto.distance", target = "distance")
    @Mapping(source = "courseDto.courseIsPublic", target = "isPublic")
    @Mapping(source = "courseDto.courseCreatedAt", target = "createdAt")
    CourseSummaryResponse toCourseSummaryResponse(CourseWithMemberDetailsDto courseDto, Integer uniqueRunnersCount,
                                                  Integer totalRunsCount, Double averageCompletionTime,
                                                  Double averageFinisherPace, Double averageFinisherCadence);

}

@Mapper(componentModel = "spring")
interface CourseSubMapper {
    @Mapping(source = "ghost.runnerUuid", target = "uuid")
    @Mapping(source = "ghost.runnerProfileUrl", target = "profileUrl")
    CourseMapResponse.MemberRecord toMemberRecordDto(CourseGhostResponse ghost);
}
