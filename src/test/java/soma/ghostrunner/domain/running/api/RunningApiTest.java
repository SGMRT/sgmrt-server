package soma.ghostrunner.domain.running.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.BDDMockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import soma.ghostrunner.ApiTestSupport;
import soma.ghostrunner.domain.running.api.dto.request.*;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RunningApiTest extends ApiTestSupport {

    // ——————————————————————————————————————————————————————————
    // 1) “새 코스 + 러닝 기록” API 테스트
    // ——————————————————————————————————————————————————————————
    @DisplayName("새로운 코스를 뛰는 API 성공 테스트")
    @Test
    void testCreateCourseAndRun() throws Exception{
        // given
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        given(runningCommandService.createCourseAndRun(any(CreateRunCommand.class), anyLong())).willReturn(new CreateCourseAndRunResponse(1L, 1L));

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
        CreateRunRequest soloRequest = validSoloCreateRunRequest();
        given(runningCommandService.createRun(any(CreateRunCommand.class), anyLong(), anyLong())).willReturn(2L);

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
        CreateRunRequest ghostRequest = validGhostCreateRunRequest(3L);
        given(runningCommandService.createRun(any(CreateRunCommand.class), anyLong(), anyLong())).willReturn(4L);

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
    @MethodSource("invalidSoloCreateRunRequests")
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

    @DisplayName("UpdateRunNameRequest 유효성 검사 실패")
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

    // ——————————————————————————————————————————————————————————
    // 4) "시계열 조회" API 테스트
    // ——————————————————————————————————————————————————————————
    @DisplayName("시계열 조회 API 성공 테스트")
    @Test
    void testGetTelemetries() throws Exception{
        // given
        given(runningQueryService.findTelemetriesById(anyLong())).willReturn(null);

        // then
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/runs/1/telemetries")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(content().string(""));
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

    // ——————————————————————————————————————————————————————————
    // 5) 러닝 공개/비공개 설정 API 테스트
    // ——————————————————————————————————————————————————————————
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

    private static Stream<Arguments> invalidSoloCreateRunRequests() {
        return Stream.of(
                Arguments.of(
                        setInvalidSoloCreateRunRequest(), "ghostRunningId"
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

    private static CreateRunRequest setInvalidGhostCreateRunRequest() {
        return validGhostCreateRunRequest(null);
    }

    private static CreateRunRequest setCreateRunRequestInvalidPausedAndPublic() {
        CreateRunRequest soloRequest = validSoloCreateRunRequest();
        soloRequest.setIsPublic(true);
        soloRequest.setHasPaused(true);
        return soloRequest;
    }
}
