package soma.ghostrunner.domain.running.api.dto;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.RunOnCourseRequest;
import soma.ghostrunner.domain.running.api.dto.request.RunRecordDto;
import soma.ghostrunner.domain.running.api.dto.request.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.CreateRunningCommand;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;

import java.util.ArrayList;
import java.util.List;

class RunningApiMapperTest {

    private final RunningApiMapper mapper = Mappers.getMapper(RunningApiMapper.class);

    @DisplayName("CreateCourseAndRunRequest -> CreateRunningCommand 매핑 테스트")
    @Test
    void toCommandFromCreateCourseAndRunRequest() {
        // given
        CreateCourseAndRunRequest request = CreateCourseAndRunRequest.builder()
                .mode("SOLO")
                .startedAt(1750729987181L)
                .record(createRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(createTelemetries())
                .build();

        // when
        CreateRunningCommand command = mapper.toCommand(request);

        // then
        // RUNNING
        Assertions.assertThat(command).isNotNull();
        Assertions.assertThat(command.mode()).isEqualTo("SOLO");
        Assertions.assertThat(command.startedAt()).isEqualTo(1750729987181L);

        // RECORD
        Assertions.assertThat(command.record().duration()).isEqualTo(3600L);
        Assertions.assertThat(command.record().avgPace()).isEqualTo(5.7);

        // TELEMETRIES
        List<TelemetryCommand> telemetryCommands = command.telemetries();
        for (int i = 0; i < telemetryCommands.size(); i++) {
            Assertions.assertThat(telemetryCommands.get(i).lat()).isEqualTo(37.5 + i);
            Assertions.assertThat(telemetryCommands.get(i).alt()).isEqualTo(100 + i);
        }
    }

    private RunRecordDto createRunRecordDto() {
        return RunRecordDto.builder()
                .duration(3600L)
                .distance(10.5)
                .avgPace(5.7)
                .calories(800)
                .altitude(120)
                .avgBpm(150)
                .avgCadence(80)
                .build();
    }

    private List<TelemetryDto> createTelemetries() {
        List<TelemetryDto> telemetryDtos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryDtos.add(TelemetryDto.builder()
                    .timeStamp(1750729987181L)
                    .lat(37.5+i).lng(127.0+i)
                    .dist(4.2).pace(5.48).alt(100+i)
                    .cadence(80).bpm(150)
                    .isRunning(true)
                    .build());
        }
        return telemetryDtos;
    }

    @DisplayName("RunOnCourseRequest -> CreateRunningCommand 매핑 테스트")
    @Test
    void toCommandFromRunOnCourseRequest() {
        // given
        RunOnCourseRequest soloRequest = RunOnCourseRequest.builder()
                .mode("SOLO")
                .startedAt(1750729987181L)
                .record(createRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(createTelemetries())
                .build();
        RunOnCourseRequest ghostRequest = RunOnCourseRequest.builder()
                .mode("GHOST")
                .ghostRunningId(2L)
                .startedAt(1750729987181L)
                .record(createRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(createTelemetries())
                .build();

        // when
        CreateRunningCommand soloCommand = mapper.toCommand(soloRequest);
        CreateRunningCommand ghostCommand = mapper.toCommand(ghostRequest);

        // then
        // RUNNING
        Assertions.assertThat(soloCommand).isNotNull();
        Assertions.assertThat(soloCommand.mode()).isEqualTo("SOLO");
        Assertions.assertThat(soloCommand.startedAt()).isEqualTo(1750729987181L);
        Assertions.assertThat(ghostCommand).isNotNull();
        Assertions.assertThat(ghostCommand.mode()).isEqualTo("GHOST");
        Assertions.assertThat(ghostCommand.ghostRunningId()).isEqualTo(2L);
        Assertions.assertThat(ghostCommand.startedAt()).isEqualTo(1750729987181L);

        // RECORD
        Assertions.assertThat(soloCommand.record().duration()).isEqualTo(3600L);
        Assertions.assertThat(ghostCommand.record().avgPace()).isEqualTo(5.7);

        // TELEMETRIES
        List<TelemetryCommand> telemetryCommands = ghostCommand.telemetries();
        for (int i = 0; i < telemetryCommands.size(); i++) {
            Assertions.assertThat(telemetryCommands.get(i).lat()).isEqualTo(37.5 + i);
            Assertions.assertThat(telemetryCommands.get(i).alt()).isEqualTo(100 + i);
        }
    }
}
