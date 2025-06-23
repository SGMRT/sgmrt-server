package soma.ghostrunner.domain.running.api.dto;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.RunOnCourseRequest;
import soma.ghostrunner.domain.running.application.dto.CreateRunningCommand;

@Mapper(componentModel = "spring")
public interface RunningApiMapper {
    RunningApiMapper INSTANCE = Mappers.getMapper(RunningApiMapper.class);

    CreateRunningCommand toCommand(CreateCourseAndRunRequest request);

    CreateRunningCommand toCommand(RunOnCourseRequest request);
}
