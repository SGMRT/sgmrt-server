package soma.ghostrunner.domain.running.application.support;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseRunDto;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse.PacemakerResponse;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse.PacemakerSetResponse;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.domain.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RunningApplicationMapper {

    RunningApplicationMapper INSTANCE = Mappers.getMapper(RunningApplicationMapper.class);

    default Running toRunning(CreateRunCommand command,
                              TelemetryStatistics processedTelemetry,
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

    default RunningRecord toRunningRecord(RunRecordCommand record, TelemetryStatistics processedTelemetry) {

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
                            TelemetryStatistics processedTelemetry,
                            RunningDataUrlsDto runningDataUrlsDto) {

        Double distance = createRunCommand.getRecord().getDistance();
        Double elevationAverage = processedTelemetry.avgElevation();
        Double elevationGain = createRunCommand.getRecord().getElevationGain();
        Double elevationLoss = createRunCommand.getRecord().getElevationLoss();
        Double startLat = processedTelemetry.startPoint().y();
        Double startLng = processedTelemetry.startPoint().x();

        String pathDataSavedUrl = runningDataUrlsDto.getSimplifiedPathSavedUrl();
        String checkpointUrl = runningDataUrlsDto.getCheckpointUrl();
        String thumbnailImageUrl = runningDataUrlsDto.getScreenShotUrl();

        return Course.of(
                member,
                distance,
                elevationAverage,
                elevationGain,
                elevationLoss,
                startLat,
                startLng,
                pathDataSavedUrl,
                checkpointUrl,
                thumbnailImageUrl
        );
    }

    @Mapping(source = "running.id", target = "runningId")
    @Mapping(source = "course.id", target = "courseId")
    CreateCourseAndRunResponse toResponse(Running running, Course course);

    default Pacemaker toPacemaker(Pacemaker.Norm norm, CreatePacemakerCommand command, Member member) {
        return Pacemaker.of(norm, command.getTargetDistance(), member.getUuid());
    }

    default PacemakerPollingResponse toResponse(Pacemaker.Status status) {
        return PacemakerPollingResponse.builder()
                .processingStatus(status.name())
                .build();
    }

    default PacemakerPollingResponse toResponse(Pacemaker p, List<PacemakerSet> sets) {
        List<PacemakerSetResponse> setResponses = sets.stream()
                .map(s -> PacemakerSetResponse.builder()
                        .setNum(s.getSetNum())
                        .message(s.getMessage())
                        .startPoint(s.getStartPoint())
                        .endPoint(s.getEndPoint())
                        .pace(s.getPace())
                        .build())
                .toList();

        PacemakerResponse pacemakerResponse = PacemakerResponse.builder()
                .id(p.getId())
                .norm(p.getNorm())
                .summary(p.getSummary())
                .goalKm(p.getGoalDistance())
                .expectedMinutes(p.getExpectedTime())
                .initialMessage(p.getInitialMessage())
                .runningId(p.getRunningId())
                .sets(setResponses)
                .build();

        return PacemakerPollingResponse.builder()
                .processingStatus(p.getStatus().name())
                .pacemaker(pacemakerResponse)
                .build();
    }

    @Mapping(source = "member.uuid", target = "runnerUuid")
    @Mapping(source = "member.profilePictureUrl", target = "runnerProfileUrl")
    @Mapping(source = "member.nickname", target = "runnerNickname")
    @Mapping(source = "id", target = "runningId")
    @Mapping(source = "runningRecord.averagePace", target = "averagePace")
    @Mapping(source = "runningRecord.cadence", target = "cadence")
    @Mapping(source = "runningRecord.bpm", target = "bpm")
    @Mapping(source = "runningRecord.duration", target = "duration")
    @Mapping(source = "createdAt", target = "startedAt")
    CourseGhostResponse toGhostResponse(Running running);

    @Mapping(target = "startedAt",
            expression = "java(java.time.LocalDateTime.ofEpochSecond(runDto.startedAt(), 0, java.time.ZoneOffset.UTC))")
    CourseGhostResponse toGhostResponse(CourseRunDto runDto);

    default List<RunInfo> toResponse(List<Running> runnings) {
        return runnings.stream()
                .map(RunInfo::new)
                .toList();
    }
  
}
