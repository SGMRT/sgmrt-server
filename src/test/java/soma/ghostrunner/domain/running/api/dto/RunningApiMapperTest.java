package soma.ghostrunner.domain.running.api.dto;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.CreateRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.RunRecordRequest;
import soma.ghostrunner.domain.running.api.dto.request.TelemetryRequest;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;

import java.util.ArrayList;
import java.util.List;

class RunningApiMapperTest {

    private final RunningApiMapper mapper = Mappers.getMapper(RunningApiMapper.class);

    @DisplayName("CreateCourseAndRunRequest를 CreateRunCommand로 변환한다.")
    @Test
    void fromCreateCourseAndRunRequestToCreateRunCommand() {
        // given
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();

        // when
        CreateRunCommand command = mapper.toCommand(request);

        // then
        Assertions.assertThat(request.getRunningName()).isEqualTo(command.runningName());
        Assertions.assertThat(request.getRecord().getDuration()).isEqualTo(command.record().duration());
        Assertions.assertThat(request.getHasPaused()).isEqualTo(command.hasPaused());
        Assertions.assertThat(request.getHasPaused()).isEqualTo(command.hasPaused());
        Assertions.assertThat(command.mode()).isEqualTo("SOLO");
        Assertions.assertThat(command.ghostRunningId()).isNull();
    }

    @DisplayName("Solo's CreateRunRequest -> CreateRunCommand")
    @Test
    void fromCreateSoloGhostRunRequestToCreateRunCommand() {
        // given
        CreateRunRequest request = validCreateSoloRunRequest();

        // when
        CreateRunCommand command = mapper.toCommand(request);

        // then
        Assertions.assertThat(request.getRunningName()).isEqualTo(command.runningName());
        Assertions.assertThat(request.getMode()).isEqualTo(command.mode());
        Assertions.assertThat(request.getRecord().getDuration()).isEqualTo(command.record().duration());
        Assertions.assertThat(request.getHasPaused()).isEqualTo(command.hasPaused());
        Assertions.assertThat(request.getHasPaused()).isEqualTo(command.hasPaused());
        Assertions.assertThat(command.ghostRunningId()).isNull();
    }

    @DisplayName("Ghost's CreateRunRequest -> CreateRunCommand")
    @Test
    void fromCreateRunRequestToCreateRunCommand() {
        // given
        CreateRunRequest request = validCreateGhostRunRequest(1L);

        // when
        CreateRunCommand command = mapper.toCommand(request);

        // then
        Assertions.assertThat(request.getRunningName()).isEqualTo(command.runningName());
        Assertions.assertThat(request.getMode()).isEqualTo(command.mode());
        Assertions.assertThat(request.getRecord().getDuration()).isEqualTo(command.record().duration());
        Assertions.assertThat(request.getHasPaused()).isEqualTo(command.hasPaused());
        Assertions.assertThat(request.getHasPaused()).isEqualTo(command.hasPaused());
        Assertions.assertThat(command.ghostRunningId()).isEqualTo(1L);
    }

    private CreateCourseAndRunRequest validCreateCourseAndRunRequest() {
        return CreateCourseAndRunRequest.builder()
                .runningName("테스트 러닝 제목")
                .startedAt(1750729987181L)
                .record(validRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(validTelemetries())
                .build();
    }

    private RunRecordRequest validRunRecordDto() {
        return RunRecordRequest.builder()
                .duration(3600L)
                .distance(10.5)
                .avgPace(5.7)
                .calories(800)
                .elevationGain(30)
                .elevationLoss(-20)
                .avgBpm(150)
                .avgCadence(80)
                .build();
    }

    private List<TelemetryRequest> validTelemetries() {
        List<TelemetryRequest> telemetryRequests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryRequests.add(TelemetryRequest.builder()
                    .timeStamp(1750729987181L)
                    .lat(37.5).lng(127.0)
                    .dist(4.2).pace(5.48).alt(100)
                    .cadence(80).bpm(150)
                    .isRunning(true)
                    .build());
        }
        return telemetryRequests;
    }

    private CreateRunRequest validCreateSoloRunRequest() {
        return CreateRunRequest.builder()
                .runningName("테스트 러닝 제목")
                .mode("SOLO")
                .startedAt(1750729987181L)
                .record(validRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(validTelemetries())
                .build();
    }

    private CreateRunRequest validCreateGhostRunRequest(Long ghostRunningId) {
        return CreateRunRequest.builder()
                .runningName("테스트 러닝 제목")
                .mode("GHOST")
                .ghostRunningId(ghostRunningId)
                .startedAt(1750729987181L)
                .record(validRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(validTelemetries())
                .build();
    }
}
