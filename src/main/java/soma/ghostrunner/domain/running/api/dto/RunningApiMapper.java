package soma.ghostrunner.domain.running.api.dto;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.CreateRunRequest;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;

@Mapper(componentModel = "spring")
public interface RunningApiMapper {
    RunningApiMapper INSTANCE = Mappers.getMapper(RunningApiMapper.class);

    CreateRunCommand toCommand(CreateCourseAndRunRequest request);

    CreateRunCommand toCommand(CreateRunRequest request);
}
