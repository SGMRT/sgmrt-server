package soma.ghostrunner.domain.running.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import soma.ghostrunner.clients.aws.S3Uploader;
import soma.ghostrunner.domain.course.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.request.RunRecordDto;
import soma.ghostrunner.domain.running.api.dto.request.TelemetryDto;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.RunOnCourseRequest;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.global.common.error.GlobalExceptionAdvice;
import soma.ghostrunner.global.common.log.HttpLogger;
import soma.ghostrunner.global.common.log.LogFilter;
import soma.ghostrunner.global.config.AwsConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RunningApi.class)
class RunningApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    RunningCommandService runningCommandService;
    @MockitoBean
    RunningApiMapper mapper;
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

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());

    }

    @DisplayName("CreateRunRequest 유효성 검사 실패")
    @ParameterizedTest(name = "[{index}] field `{1}` invalid")
    @MethodSource("invalidCreateCourseAndRunRequests")
    void testCreateCourseAndRunValidationError(CreateCourseAndRunRequest payload, String wrongField) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField));;
    }

    private static Stream<Arguments> invalidCreateCourseAndRunRequests() {
        return Stream.of(
                Arguments.of(
                        setCourseNameBlank(), "courseName"
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
                )
        );
    }

    private static CreateCourseAndRunRequest validCreateCourseAndRunRequest() {
        return CreateCourseAndRunRequest.builder()
                .courseName("반포 코스")
                .mode("SOLO")
                .startedAt(LocalDateTime.of(2025, 6, 20, 14, 5, 20))
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
                .altitude(120)
                .avgBpm(150)
                .avgCadence(80)
                .build();
    }

    private static List<TelemetryDto> validTelemetries() {
        List<TelemetryDto> telemetryDtos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            telemetryDtos.add(TelemetryDto.builder()
                    .timeStamp(LocalDateTime.parse("2025-06-18T08:00:00"))
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
        request.setCourseName("");
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

    // ——————————————————————————————————————————————————————————
    // 2) “기존 코스 러닝” API 테스트
    // ——————————————————————————————————————————————————————————
    @DisplayName("혼자 기존 코스를 뛰는 API 성공 테스트")
    @Test
    void testSoloRunExistingCourse() throws Exception{
        // given
        RunOnCourseRequest soloRequest = validSoloRunOnCourseRequest();

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L + "/" + 1L)
                        .content(objectMapper.writeValueAsString(soloRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());

    }

    @DisplayName("고스트와 기존 코스를 뛰는 API 성공 테스트")
    @Test
    void testGhostRunExistingCourse() throws Exception{
        // when
        RunOnCourseRequest ghostRequest = validGhostRunOnCourseRequest(3L);

        // then
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/" + 1L + "/" + 1L)
                        .content(objectMapper.writeValueAsString(ghostRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk());

    }

    @DisplayName("기존 코스를 뛰는 API - 유효성 검사")
    @ParameterizedTest(name = "[{index}] field `{1}` invalid")
    @MethodSource("invalidSoloRunOnCourseRequests")
    void testRunExistingCourseValidationError(RunOnCourseRequest payload, String wrongField) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/runs/1/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrorInfos[0].field").value(wrongField));
    }

    private static RunOnCourseRequest validSoloRunOnCourseRequest() {
        return RunOnCourseRequest.builder()
                .mode("SOLO")
                .startedAt(LocalDateTime.of(2025, 6, 20, 14, 5, 20))
                .record(validRunRecordDto())
                .hasPaused(false)
                .isPublic(true)
                .telemetries(validTelemetries())
                .build();
    }

    private static RunOnCourseRequest validGhostRunOnCourseRequest(Long ghostRunningId) {
        return RunOnCourseRequest.builder()
                .mode("GHOST")
                .ghostRunningId(ghostRunningId)
                .startedAt(LocalDateTime.of(2025, 6, 20, 14, 5, 20))
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

    private static RunOnCourseRequest setInvalidSoloRunOnCourseRequest() {
        RunOnCourseRequest soloRequest = validSoloRunOnCourseRequest();
        soloRequest.setGhostRunningId(2L);
        return soloRequest;
    }

    private static RunOnCourseRequest setInvalidGhostRunOnCourseRequest() {
        return validGhostRunOnCourseRequest(null);
    }

    private static RunOnCourseRequest setRunOnCourseRequestInvalidPausedAndPublic() {
        RunOnCourseRequest soloRequest = validSoloRunOnCourseRequest();
        soloRequest.setIsPublic(true);
        soloRequest.setHasPaused(true);
        return soloRequest;
    }
}
