package soma.ghostrunner.domain.running.application.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

@Mapper(componentModel = "spring")
public interface RunningServiceMapper {

    RunningServiceMapper INSTANCE = Mappers.getMapper(RunningServiceMapper.class);

    default Running toRunning(CreateRunCommand command,
                              ProcessedTelemetriesDto processedTelemetry,
                              RunningDataUrlsDto runningDataUrlsDto,
                              Member member,
                              Course course) {

        RunningRecord runningRecord = toRunningRecord(command.getRecord(), processedTelemetry);
        RunningMode mode = RunningMode.valueOf(command.getMode());

        return Running.of(
                command.getRunningName(),
                mode,
                command.getGhostRunningId(),
                runningRecord,
                command.getStartedAt(),
                command.getIsPublic(),
                command.getHasPaused(),
                runningDataUrlsDto.getRawTelemetryUrl(),
                runningDataUrlsDto.getInterpolatedTelemetryUrl(),
                runningDataUrlsDto.getScreenShotUrl(),
                member,
                course
        );
    }

    default RunningRecord toRunningRecord(RunRecordCommand record, ProcessedTelemetriesDto processedTelemetry) {

        return RunningRecord.of(
                record.getDistance(),
                processedTelemetry.avgElevation(),
                record.getElevationGain(),
                record.getElevationLoss(),
                record.getAvgPace(),
                processedTelemetry.highestPace(),
                processedTelemetry.lowestPace(),
                record.getDuration(),
                record.getCalories(),
                record.getAvgCadence(),
                record.getAvgBpm()
        );
    }

    default Course toCourse(Member member,
                            CreateRunCommand createRunCommand,
                            ProcessedTelemetriesDto processedTelemetry,
                            String pathDataSavedUrl,
                            String thumbnailImageUrl) {

        Double distance = createRunCommand.getRecord().getDistance();
        Double elevationAverage = processedTelemetry.avgElevation();
        Double elevationGain = createRunCommand.getRecord().getElevationGain();
        Double elevationLoss = createRunCommand.getRecord().getElevationLoss();
        Double startLat = processedTelemetry.startPoint().lat();
        Double startLng = processedTelemetry.startPoint().lng();

        return Course.of(
                member,
                distance,
                elevationAverage,
                elevationGain,
                elevationLoss,
                startLat,
                startLng,
                pathDataSavedUrl,
                thumbnailImageUrl
        );
    }

    @Mapping(source = "running.id", target = "runningId")
    @Mapping(source = "course.id", target = "courseId")
    CreateCourseAndRunResponse toResponse(Running running, Course course);

    CoordinateDto toCoordinateDto(CoordinateDtoWithTs coordinateDtoWithTs);
  
}
