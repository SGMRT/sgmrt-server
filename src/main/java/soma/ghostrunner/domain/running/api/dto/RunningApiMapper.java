package soma.ghostrunner.domain.running.api.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.CreateRunRequest;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.domain.Running;

@Mapper(componentModel = "spring")
public interface RunningApiMapper {

    RunningApiMapper INSTANCE = Mappers.getMapper(RunningApiMapper.class);

    @Mapping(target = "mode", constant = "SOLO")
    CreateRunCommand toCommand(CreateCourseAndRunRequest request);

    CreateRunCommand toCommand(CreateRunRequest request);

    @Mapping(source = "member.id", target = "runnerId")
    @Mapping(source = "member.profilePictureUrl", target = "runnerProfileUrl")
    @Mapping(source = "member.nickname", target = "runnerNickname")
    @Mapping(source = "id", target = "runningId")
    @Mapping(source = "runningRecord.averagePace", target = "averagePace")
    @Mapping(source = "runningRecord.cadence", target = "cadence")
    @Mapping(source = "runningRecord.bpm", target = "bpm")
    @Mapping(source = "runningRecord.duration", target = "duration")
    @Mapping(source = "createdAt", target = "startedAt")
    CourseGhostResponse toGhostResponse(Running running);

}
