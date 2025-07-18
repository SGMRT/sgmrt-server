package soma.ghostrunner.domain.course.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.response.*;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.support.CoordinateConverter;

import java.util.List;

@Mapper(componentModel = "spring", uses = {CoordinateConverter.class})
public interface CourseMapper {
    @Mapping(source = "startPoint.latitude", target = "startLat")
    @Mapping(source = "startPoint.longitude", target = "startLng")
    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
    @Mapping(source = "courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "courseProfile.elevationLoss", target = "elevationLoss")
    CourseResponse toCourseResponse(Course course);

    @Mapping(target = "distance",
            expression = "java(course.getCourseProfile() != null && course.getCourseProfile().getDistance() != null " +
                    "? (int) (course.getCourseProfile().getDistance() * 1000) " +
                    ": null)")
    @Mapping(source = "course.courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "course.courseProfile.elevationLoss", target = "elevationLoss")
    CourseDetailedResponse toCourseDetailedResponse(Course course,
                                                    Double averageCompletionTime, Double averageFinisherPace,
                                                    Double averageFinisherCadence, Double lowestFinisherPace);

    @Mapping(source = "running.id", target = "runningId")
    @Mapping(source = "running.runningRecord.duration", target = "duration")
    @Mapping(source = "running.runningRecord.bpm", target = "bpm")
    @Mapping(source = "running.runningRecord.averagePace", target = "averagePace")
    CourseRankingResponse toRankingResponse(Running running, Integer rank);

    CourseCoordinatesResponse toCoordinatesResponse(Course course, List<CoordinateDto> coordinates);

    @Mapping(source = "course.id", target = "courseId")
    @Mapping(source = "course.name", target = "courseName")
    @Mapping(source = "course.startPoint.latitude", target = "startLat")
    @Mapping(source = "course.startPoint.longitude", target = "startLng")
    @Mapping(source = "course.courseProfile.distance", target = "distance")
    @Mapping(source = "course.isPublic", target = "isPublic")
    @Mapping(target = "courseCreatedAt",
             expression = "java(LocalDateTime.ofInstant(Instant.ofEpochMilli(course.createdAt), " +
                                                        "ZoneId.of(\"Asia/Seoul\")")
    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.uuid", target = "memberUuid")
    @Mapping(source = "member.nickname", target = "memberNickname")
    @Mapping(source = "member.profilePictureUrl", target = "memberProfilePictureUrl")
    CourseWithMemberDetailsDto toCourseWithMemberDetailsDto(Course course, Member member);

    @Mapping(source = "courseDto.courseId", target = "id")
    @Mapping(source = "courseDto.courseName", target = "name")
    @Mapping(source = "courseDto.distance", target = "distance")
    @Mapping(source = "courseDto.courseIsPublic", target = "isPublic")
    @Mapping(source = "courseDto.courseCreatedAt", target = "createdAt")
    CourseSummaryResponse toCourseSummaryResponse(CourseWithMemberDetailsDto courseDto, Integer uniqueRunnersCount, Integer totalRunsCount,
                                                  Integer averageCompletionTime, Double averageFinisherPace,
                                                  Integer averageFinisherCadence);

}
