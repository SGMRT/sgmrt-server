package soma.ghostrunner.domain.running.application.dto;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordDto;
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

        RunningRecord runningRecord = toRunningRecord(command.record(), processedTelemetry);
        RunningMode mode = RunningMode.valueOf(command.mode());

        return Running.of(
                command.runningName(),
                mode,
                command.ghostRunningId(),
                runningRecord,
                command.startedAt(),
                command.isPublic(),
                command.hasPaused(),
                runningDataUrlsDto.getRawTelemetrySavedUrl(),
                runningDataUrlsDto.getInterpolatedTelemetrySavedUrl(),
                runningDataUrlsDto.getScreenShotSavedUrl(),
                member,
                course
        );
    }

    default RunningRecord toRunningRecord(RunRecordDto record, ProcessedTelemetriesDto processedTelemetry) {

        return RunningRecord.of(
                record.distance(),
                processedTelemetry.avgElevation(),
                record.elevationGain(),
                record.elevationLoss(),
                record.avgPace(),
                processedTelemetry.highestPace(),
                processedTelemetry.lowestPace(),
                record.duration(),
                record.calories(),
                record.avgCadence(),
                record.avgBpm()
        );
    }

    default Course toCourse(Member member,
                            CreateRunCommand createRunCommand,
                            ProcessedTelemetriesDto processedTelemetry,
                            String pathDataSavedUrl,
                            String thumbnailImageUrl) {

        Double distance = createRunCommand.record().distance();
        Double elevationAverage = processedTelemetry.avgElevation();
        Double elevationGain = createRunCommand.record().elevationGain();
        Double elevationLoss = createRunCommand.record().elevationLoss();
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

}
