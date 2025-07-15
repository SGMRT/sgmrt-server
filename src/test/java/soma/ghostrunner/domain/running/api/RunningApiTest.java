package soma.ghostrunner.domain.running.api;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Collections;
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
    void createInvalidCourseAndRun(CreateCourseAndRunRequest payload, String wrongField, String reason) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField))
                .andExpect(jsonPath("$.fieldErrorInfos[0].reason").value(reason));
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
                        setCourseNameBlank(), "runningName", "must not be blank"
                ),
                Arguments.of(
                        setStartedAtNull(), "startedAt", "must not be null"
                ),
                Arguments.of(
                        setHasPausedNull(), "hasPaused", "must not be null"
                ),
                Arguments.of(
                        setElevationGainNull(), "record.elevationGain", "must not be null"
                ),
                Arguments.of(
                        setRunningRecordDurationNegative(), "record.duration", "must be greater than or equal to 0"
                ),
                Arguments.of(
                        setElevationLossPositive(), "record.elevationLoss", "must be less than or equal to 0"
                ),
                Arguments.of(
                        setHasPausedAndIsPublicInvalid(), "hasPaused", "중지한 기록이 있다면 공개 설정이 불가능합니다."
                ),
                Arguments.of(
                        setEmptyTelemetriesCreateCourseAndRunRequest(), "telemetries", "must not be empty"
                ),
                Arguments.of(
                        setNullTelemetriesCreateCourseAndRunRequest(), "telemetries", "must not be empty"
                )
        );
    }

    private static CreateCourseAndRunRequest setCourseNameBlank() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setRunningName("");
        return request;
    }

    private static CreateCourseAndRunRequest setStartedAtNull() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setStartedAt(null);
        return request;
    }

    private static CreateCourseAndRunRequest setHasPausedNull() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setHasPaused(null);
        return request;
    }

    private static CreateCourseAndRunRequest setElevationGainNull() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.getRecord().setElevationGain(null);
        return request;
    }

    private static CreateCourseAndRunRequest setRunningRecordDurationNegative() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.getRecord().setDuration(-1L);
        return request;
    }

    private static CreateCourseAndRunRequest setElevationLossPositive() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.getRecord().setElevationLoss(20);
        return request;
    }

    private static CreateCourseAndRunRequest setHasPausedAndIsPublicInvalid() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setHasPaused(true);
        request.setIsPublic(true);
        return request;
    }

    private static CreateCourseAndRunRequest setEmptyTelemetriesCreateCourseAndRunRequest() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setTelemetries(Collections.emptyList());
        return request;
    }

    private static CreateCourseAndRunRequest setNullTelemetriesCreateCourseAndRunRequest() {
        CreateCourseAndRunRequest request = validCreateCourseAndRunRequest();
        request.setTelemetries(null);
        return request;
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
    void testCreateRunValidation(CreateRunRequest payload, String wrongField, String reason) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/courses/1/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField))
                .andExpect(jsonPath("$.fieldErrorInfos[0].reason").value(reason));
    }

    private static Stream<Arguments> invalidSoloCreateRunRequests() {
        return Stream.of(
                Arguments.of(
                        setInvalidSoloCreateRunRequest(), "ghostRunningId", "러닝 모드에 따라 고스트 ID 값의 규칙이 지켜지지 않았습니다."
                ),
                Arguments.of(
                        setInvalidModeCreateRunRequest(), "mode", "유효하지 않은 러닝모드입니다."
                ),
                Arguments.of(
                        setInvalidGhostCreateRunRequest(), "ghostRunningId", "러닝 모드에 따라 고스트 ID 값의 규칙이 지켜지지 않았습니다."
                ),
                Arguments.of(
                        setCreateRunRequestInvalidPausedAndPublic(), "hasPaused", "중지한 기록이 있다면 공개 설정이 불가능합니다."
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

    @DisplayName("러닝 기록을 삭제한다.")
    @Test
    void deleteRunnings() throws Exception {
        // given
        DeleteRunningRequest request = DeleteRunningRequest.builder()
                .runningIds(List.of(1L, 2L, 3L))
                .build();

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());
    }

    @DisplayName("삭제할 러닝 기록의 ID 리스트가 Null이거나 비어서는 안된다.")
    @Test
    void deleteRunningsCannotBeEmpty() throws Exception {
        // given
        DeleteRunningRequest request = DeleteRunningRequest.builder()
                .runningIds(List.of())
                .build();

        // when // then
        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value("runningIds"))
                .andExpect(jsonPath("$.fieldErrorInfos[0].reason").value("must not be empty"));
    }

    private static Stream<Arguments> createInvalidDeleteRunningRequest() {
        return Stream.of(
                Arguments.of(
                        setInvalidSoloCreateRunRequest(), "ghostRunningId", "러닝 모드에 따라 고스트 ID 값의 규칙이 지켜지지 않았습니다."
                ),
                Arguments.of(
                        setInvalidModeCreateRunRequest(), "mode", "유효하지 않은 러닝모드입니다."
                ),
                Arguments.of(
                        setInvalidGhostCreateRunRequest(), "ghostRunningId", "러닝 모드에 따라 고스트 ID 값의 규칙이 지켜지지 않았습니다."
                ),
                Arguments.of(
                        setCreateRunRequestInvalidPausedAndPublic(), "hasPaused", "중지한 기록이 있다면 공개 설정이 불가능합니다."
                )
        );
    }

}
