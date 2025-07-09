package soma.ghostrunner.domain.course.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.response.CourseResponse;
import soma.ghostrunner.domain.running.domain.support.CourseCoordinateConverter;

@Mapper(componentModel = "spring", uses = {CourseCoordinateConverter.class})
public interface CourseMapper {
    @Mapping(source = "startPoint.latitude", target = "startLat")
    @Mapping(source = "startPoint.longitude", target = "startLng")
    @Mapping(source = "courseProfile.distance", target = "distance")
    @Mapping(source = "courseProfile.elevationGain", target = "elevationGain")
    @Mapping(source = "courseProfile.elevationLoss", target = "elevationLoss")
    CourseResponse toCourseResponse(Course course);
}
