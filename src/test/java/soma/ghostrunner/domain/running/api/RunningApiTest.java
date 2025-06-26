package soma.ghostrunner.domain.running.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapperImpl;
import soma.ghostrunner.domain.running.api.dto.request.*;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.dto.CreateRunningCommand;
import soma.ghostrunner.domain.running.application.dto.RunRecordCommand;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;
import soma.ghostrunner.global.common.log.HttpLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RunningApi.class)
@Import(RunningApiMapperImpl.class)
class RunningApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RunningApiMapper runningApiMapper;

    @MockitoBean
    RunningCommandService runningCommandService;
    @MockitoBean
    HttpLogger httpLogger;

    // ——————————————————————————————————————————————————————————
    // 1) “새 코스 + 러닝 기록” API 테스트
    // ——————————————————————————————————————————————————————————
    @DisplayName("새로운 코스를 뛰는 API 성공 테스트")
    @Test
    void testCreateCourseAndRun() throws Exception{
        // given
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        CreateRunningCommand command = validCreateSoloRunningCommand();
        BDDMockito.given(runningCommandService.createCourseAndRun(command, 1L)).willReturn(new CreateCourseAndRunResponse(1L, 1l));

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runningId").value(1L))
                .andExpect(jsonPath("$.courseId").value(1L));

    }

    @DisplayName("CreateRunRequest 유효성 검사 실패")
    @ParameterizedTest(name = "[{index}] field `{1}` invalid")
    @MethodSource("invalidCreateCourseAndRunRequests")
    void testCreateCourseAndRunValidation(CreateCourseAndRunRequest payload, String wrongField) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField));;
    }

    // ——————————————————————————————————————————————————————————
    // 2) “기존 코스 러닝” API 테스트
    // ——————————————————————————————————————————————————————————
    @DisplayName("혼자 기존 코스를 뛰는 API 성공 테스트")
    @Test
    void testSoloCreateRun() throws Exception{
        // given
        CreateRunRequest soloRequest = validSoloRunOnCourseRequest();
        CreateRunningCommand command = validCreateSoloRunningCommand();
        BDDMockito.given(runningCommandService.createRun(command, 1L, 1L)).willReturn(2L);

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L + "/" + 1L)
                        .content(objectMapper.writeValueAsString(soloRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }

    @DisplayName("고스트와 기존 코스를 뛰는 API 성공 테스트")
    @Test
    void testGhostCreateRun() throws Exception{
        // when
        CreateRunRequest ghostRequest = validGhostRunOnCourseRequest(3L);
        CreateRunningCommand command = validCreateGhostRunningCommand(3L);
        BDDMockito.given(runningCommandService.createRun(command, 1L, 1L)).willReturn(4L);

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L + "/" + 1L)
                        .content(objectMapper.writeValueAsString(ghostRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().string("4"));
    }

    @DisplayName("기존 코스를 뛰는 API - 유효성 검사")
    @ParameterizedTest(name = "[{index}] field `{1}` invalid")
    @MethodSource("invalidSoloRunOnCourseRequests")
    void testCreateRunValidation(CreateRunRequest payload, String wrongField) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/1/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField));
    }

    // ——————————————————————————————————————————————————————————
    // 3) "러닝 기록 제목 수정" API 테스트
    // ——————————————————————————————————————————————————————————
    @DisplayName("새로운 코스를 뛰는 API 성공 테스트")
    @Test
    void testUpdateRunningName() throws Exception{
        // given
        UpdateRunNameRequest request = new UpdateRunNameRequest("수정할 이름");

        // then
        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/runs/1/name/1")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());

    }

    @DisplayName("UpdateRunNameRequest 유효성 검사 실패")
    @Test
    void testUpdateRunningNameValidation() throws Exception {
        // given
        UpdateRunNameRequest request = new UpdateRunNameRequest("수정할 이름");

        // when
        request.setName("");

        // then
        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/runs/1/name/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value("name"));;
    }

    private static Stream<Arguments> invalidCreateCourseAndRunRequests() {
        return Stream.of(
                Arguments.of(
                        setCourseNameBlank(), "runningName"
                ),
                Arguments.of(
                        setHasPausedNull(), "hasPaused"
                ),
                Arguments.of(
                        setRunningModeInvalid(), "mode"
                ),
                Arguments.of(
                        setRunningRecordDurationMinus(), "record.duration"
                ),
                Arguments.of(
                        setHasPausedAndIsPublicInvalid(), "hasPaused"
                ),
                Arguments.of(
                        setElevationGainInvalid(), "record.elevationGain"
                ),
                Arguments.of(
                        setElevationLossInvalid(), "record.elevationLoss"
                )
        );
    }

    private static CreateRunningCommand validCreateSoloRunningCommand() {
        RunRecordCommand runRecordCommand = new RunRecordCommand(10.5, 30, -20, 3600L, 5.7, 800, 150, 80);
        List<TelemetryCommand> telemetryCommands = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryCommands.add(new TelemetryCommand(1750729987181L, 37.5, 127.0, 4.2, 5.48, 100, 80, 150, true));
        }
        return new CreateRunningCommand("테스트 러닝 제목", null, "SOLO", 1750729987181L,
                runRecordCommand, false, true, telemetryCommands);
    }

    private static CreateRunningCommand validCreateGhostRunningCommand(Long ghostRunningId) {
        RunRecordCommand runRecordCommand = new RunRecordCommand(10.5, 30, -20, 3600L, 5.7, 800, 150, 80);
        List<TelemetryCommand> telemetryCommands = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryCommands.add(new TelemetryCommand(1750729987181L, 37.5, 127.0, 4.2, 5.48, 100, 80, 150, true));
        }
        return new CreateRunningCommand("테스트 러닝 제목", ghostRunningId, "GHOST", 1750729987181L,
                runRecordCommand, false, true, telemetryCommands);
    }

    private static CreateCourseAndRunRequest validCreateCourseAndRunRequest() {
        return CreateCourseAndRunRequest.builder()
                .runningName("테스트 러닝 제목")
                .mode("SOLO")
                .startedAt(1750729987181L)
                .record(validRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(validTelemetries())
                .build();
    }

    private static RunRecordDto validRunRecordDto() {
        return RunRecordDto.builder()
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

    private static List<TelemetryDto> validTelemetries() {
        List<TelemetryDto> telemetryDtos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryDtos.add(TelemetryDto.builder()
                    .timeStamp(1750729987181L)
                    .lat(37.5).lng(127.0)
                    .dist(4.2).pace(5.48).alt(100)
                    .cadence(80).bpm(150)
                    .isRunning(true)
                    .build());
        }
        return telemetryDtos;
    }

    private static CreateCourseAndRunRequest setCourseNameBlank() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setRunningName("");
        return request;
    }

    private static CreateCourseAndRunRequest setHasPausedNull() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setHasPaused(null);
        return request;
    }

    private static CreateCourseAndRunRequest setRunningModeInvalid() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setMode("이복둥");
        return request;
    }

    private static CreateCourseAndRunRequest setRunningRecordDurationMinus() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.getRecord().setDuration(-1L);
        return request;
    }

    private static CreateCourseAndRunRequest setHasPausedAndIsPublicInvalid() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setHasPaused(true);
        request.setIsPublic(true);
        return request;
    }

    private static CreateCourseAndRunRequest setElevationGainInvalid() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.getRecord().setElevationGain(null);
        return request;
    }

    private static CreateCourseAndRunRequest setElevationLossInvalid() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.getRecord().setElevationLoss(20);
        return request;
    }

    private static CreateRunRequest validSoloRunOnCourseRequest() {
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

    private static CreateRunRequest validGhostRunOnCourseRequest(Long ghostRunningId) {
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

    private static Stream<Arguments> invalidSoloRunOnCourseRequests() {
        return Stream.of(
                Arguments.of(
                        setInvalidSoloRunOnCourseRequest(), "ghostRunningId"
                ),
                Arguments.of(
                        setInvalidGhostRunOnCourseRequest(), "ghostRunningId"
                ),
                Arguments.of(
                        setRunOnCourseRequestInvalidPausedAndPublic(), "hasPaused"
                )
        );
    }

    private static CreateRunRequest setInvalidSoloRunOnCourseRequest() {
        CreateRunRequest soloRequest = validSoloRunOnCourseRequest();
        soloRequest.setGhostRunningId(2L);
        return soloRequest;
    }

    private static CreateRunRequest setInvalidGhostRunOnCourseRequest() {
        return validGhostRunOnCourseRequest(null);
    }

    private static CreateRunRequest setRunOnCourseRequestInvalidPausedAndPublic() {
        CreateRunRequest soloRequest = validSoloRunOnCourseRequest();
        soloRequest.setIsPublic(true);
        soloRequest.setHasPaused(true);
        return soloRequest;
    }
}
