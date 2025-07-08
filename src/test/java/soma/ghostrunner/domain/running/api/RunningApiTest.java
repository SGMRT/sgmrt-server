package soma.ghostrunner.domain.running.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.running.api.dto.request.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RunningApiTest extends ApiTestSupport {

    @DisplayName("새로운 코스를 기반으로 달린다.")
    @Test
    void testCreateCourseAndRun() throws Exception{
        // given
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("새로운 코스를 기반으로 러닝할 때 CreateRunRequest 에 대한 검증을 진행한다.")
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

    private static CreateCourseAndRunRequest validCreateCourseAndRunRequest() {
        return CreateCourseAndRunRequest.builder()
                .runningName("테스트 러닝 제목")
                .startedAt(1750729987181L)
                .record(validRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(validTelemetries())
                .build();
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

    @DisplayName("기존 코스를 기반으로 혼자 달린다.")
    @Test
    void testSoloCreateRun() throws Exception{
        // given
        CreateRunRequest soloRequest = validSoloCreateRunRequest();
        given(runningCommandService.createRun(any(CreateRunCommand.class), anyLong(), anyLong())).willReturn(2L);

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/courses/" + 1L + "/" + 1L)
                        .content(objectMapper.writeValueAsString(soloRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }

    @DisplayName("기존 코스를 기반으로 고스트와 달린다.")
    @Test
    void testGhostCreateRun() throws Exception{
        // when
        CreateRunRequest ghostRequest = validGhostCreateRunRequest(3L);

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/courses/" + 1L + "/" + 1L)
                        .content(objectMapper.writeValueAsString(ghostRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("기존 코스를 기반으로 러닝할 때 CreateRunRequest에 대한 유효성 검사를 진행한다.")
    @ParameterizedTest(name = "[{index}] field `{1}` invalid")
    @MethodSource("invalidSoloCreateRunRequests")
    void testCreateRunValidation(CreateRunRequest payload, String wrongField) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/courses/1/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField));
    }

    private static Stream<Arguments> invalidSoloCreateRunRequests() {
        return Stream.of(
                Arguments.of(
                        setInvalidSoloCreateRunRequest(), "ghostRunningId"
                ),
                Arguments.of(
                        setInvalidModeCreateRunRequest(), "mode"
                ),
                Arguments.of(
                        setInvalidGhostCreateRunRequest(), "ghostRunningId"
                ),
                Arguments.of(
                        setCreateRunRequestInvalidPausedAndPublic(), "hasPaused"
                )
        );
    }

    private static CreateRunRequest setInvalidSoloCreateRunRequest() {
        CreateRunRequest soloRequest = validSoloCreateRunRequest();
        soloRequest.setGhostRunningId(2L);
        return soloRequest;
    }

    private static CreateRunRequest setInvalidModeCreateRunRequest() {
        CreateRunRequest soloRequest = validSoloCreateRunRequest();
        soloRequest.setMode("유효하지 않은 러닝모드");
        return soloRequest;
    }

    private static CreateRunRequest setInvalidGhostCreateRunRequest() {
        return validGhostCreateRunRequest(null);
    }

    private static CreateRunRequest setCreateRunRequestInvalidPausedAndPublic() {
        CreateRunRequest soloRequest = validSoloCreateRunRequest();
        soloRequest.setIsPublic(true);
        soloRequest.setHasPaused(true);
        return soloRequest;
    }

    @DisplayName("러닝 이름을 수정한다.")
    @Test
    void testPatchRunningName() throws Exception{
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

    @DisplayName("수정할 이름 요청 값은 Null 혹은 빈칸이어서는 안된다.")
    @Test
    void testPatchRunningNameValidation() throws Exception {
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

    @DisplayName("러닝 ID에 대한 시계열 데이터를 조회한다.")
    @Test
    void testGetTelemetries() throws Exception{
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/runs/1/telemetries")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("러닝 공개 상태를 변경한다.")
    @Test
    void patchRunningPublicStatus() throws Exception {
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/runs/1/isPublic")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("러닝 ID에 대한 시계열 데이터를 조회한다.")
    @Test
    void getCoordinateTelemetries() throws Exception{
        // when // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/runs/courses/1/telemetries")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    private static RunRecordRequest validRunRecordDto() {
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

    private static List<TelemetryRequest> validTelemetries() {
        List<TelemetryRequest> telemetryRequests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryRequests.add(TelemetryRequest.builder()
                    .timeStamp(1750729987181L)
                    .lat(37.5).lng(127.0)
                    .dist(4.2).pace(5.48).alt(0)
                    .cadence(80).bpm(150)
                    .isRunning(true)
                    .build());
        }
        return telemetryRequests;
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

    private static CreateRunRequest validSoloCreateRunRequest() {
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

    private static CreateRunRequest validGhostCreateRunRequest(Long ghostRunningId) {
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
