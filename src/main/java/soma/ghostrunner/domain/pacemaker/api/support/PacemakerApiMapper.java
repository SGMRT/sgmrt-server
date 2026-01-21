package soma.ghostrunner.domain.pacemaker.api.support;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.pacemaker.api.dto.request.CreatePacemakerRequest;
import soma.ghostrunner.domain.pacemaker.application.dto.request.CreatePacemakerCommand;

@Mapper(componentModel = "spring")
public interface PacemakerApiMapper {

    PacemakerApiMapper INSTANCE = Mappers.getMapper(PacemakerApiMapper.class);

    @Mapping(target = "localDate", expression = "java(java.time.LocalDate.now())")
    CreatePacemakerCommand toCommand(CreatePacemakerRequest request);

}
