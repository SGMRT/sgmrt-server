package soma.ghostrunner.domain.course.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.dto.response.CourseRankingResponse;
import soma.ghostrunner.domain.course.dto.response.CourseResponse;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.support.CoordinateConverter;

@Mapper(componentModel = "spring", uses = {CoordinateConverter.class})
public interface CourseMapper {
    @Mapping(source = "startPoint.latitude", target = "startLat")
    @Mapping(source = "startPoint.longitude", target = "startLng")
    @Mapping(source = "courseProfile.distance", target = "distance")
    @Mapping(source = "courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "courseProfile.elevationLoss", target = "elevationLoss")
    CourseResponse toCourseResponse(Course course);

    @Mapping(source = "course.courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "course.courseProfile.elevationLoss", target = "elevationLoss")
    CourseDetailedResponse toCourseDetailedResponse(Course course, Double averageCompletionTime, Double averageFinisherPace, Double averageFinisherCadence, Double lowestFinisherPace);

    @Mapping(source = "running.id", target = "runningId")
    @Mapping(source = "running.runningRecord.duration", target = "duration")
    @Mapping(source = "running.runningRecord.bpm", target = "bpm")
    @Mapping(source = "running.runningRecord.averagePace", target = "averagePace")
    CourseRankingResponse toRankingResponse(Running running, Integer rank);
}
