package soma.ghostrunner.domain.running.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.CreateRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.DeleteRunningRequest;
import soma.ghostrunner.domain.running.api.dto.request.UpdateRunNameRequest;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.application.support.RunningInfoFilter;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.common.validator.enums.EnumValid;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RunningApi {

    private final RunningQueryService runningQueryService;
    private final RunningCommandService runningCommandService;
    private final RunningApiMapper mapper;
  
    @GetMapping("/")
    public String hello() {
        return "Hello World";
    }
  
    @PostMapping("/v1/runs")
    public CreateCourseAndRunResponse createCourseAndRun(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestPart("req") @Valid CreateCourseAndRunRequest req,
            @RequestPart MultipartFile rawTelemetry,
            @RequestPart MultipartFile interpolatedTelemetry,
            @RequestPart(required = false) MultipartFile screenShotImage) {
        validateTelemetryFiles(rawTelemetry, interpolatedTelemetry);
        validateScreenShotFiles(screenShotImage);
        String memberUuid = userDetails.getUserId();
        return runningCommandService.createCourseAndRun(
                mapper.toCommand(req), memberUuid, rawTelemetry, interpolatedTelemetry, screenShotImage);
    }

    private void validateScreenShotFiles(MultipartFile screenShotImage) {
        if (screenShotImage != null) {
            if (screenShotImage.isEmpty()) {
                throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "ScreenShot MultipartFile이 비어있습니다.");
            }
        }
    }

    private void validateTelemetryFiles(MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry) {
        if (isEmpty(rawTelemetry) || isEmpty(interpolatedTelemetry)) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "Telemetry MultipartFile이 비어있습니다.");
        }
    }

    private boolean isEmpty(MultipartFile file) {
        return (file == null || file.isEmpty());
    }

    @PostMapping("/v1/runs/courses/{courseId}")
    public Long createRun(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestPart("req") @Valid CreateRunRequest req,
            @RequestPart MultipartFile rawTelemetry,
            @RequestPart MultipartFile interpolatedTelemetry,
            @RequestPart MultipartFile screenShotImage,
            @PathVariable Long courseId) {
        validateTelemetryFiles(rawTelemetry, interpolatedTelemetry);
        validateScreenShotFiles(screenShotImage);
        String memberUuid = userDetails.getUserId();
        return runningCommandService.createRun(
                mapper.toCommand(req), memberUuid, courseId, rawTelemetry, interpolatedTelemetry, screenShotImage);
    }

    @PatchMapping("/v1/runs/{runningId}/name")
    public void patchRunningName(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestBody @Valid UpdateRunNameRequest req, @PathVariable Long runningId) {
        String memberUuid = userDetails.getUserId();
        runningCommandService.updateRunningName(req.getName(), runningId, memberUuid);
    }

    @GetMapping("/v1/runs/{runningId}/telemetries")
    public String getRunningTelemetries(
            @AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long runningId) {
        String memberUuid = userDetails.getUserId();
        return runningQueryService.findRunningTelemetries(runningId, memberUuid);
    }

    @GetMapping("/v1/runs/{runningId}")
    public SoloRunDetailInfo getSoloRunInfo(
            @AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long runningId) {
        String memberUuid = userDetails.getUserId();
        return runningQueryService.findSoloRunInfo(runningId, memberUuid);
    }

    @GetMapping("/v1/runs/{myRunningId}/ghosts/{ghostRunningId}")
    public GhostRunDetailInfo getGhostRunInfo(
            @AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long myRunningId, @PathVariable Long ghostRunningId) {
        String memberUuid = userDetails.getUserId();
        return runningQueryService.findGhostRunInfo(myRunningId, ghostRunningId, memberUuid);
    }

    @PatchMapping("/v1/runs/{runningId}/isPublic")
    public void patchRunningPublicStatus(
            @AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long runningId) {
        String memberUuid = userDetails.getUserId();
        runningCommandService.updateRunningPublicStatus(runningId, memberUuid);
    }

    @DeleteMapping("/v1/runs")
    public void deleteRunnings(
            @AuthenticationPrincipal JwtUserDetails userDetails, @RequestBody @Valid DeleteRunningRequest request) {
        String memberUuid = userDetails.getUserId();
        runningCommandService.deleteRunnings(request.getRunningIds(), memberUuid);
    }

    @GetMapping("/v1/runs")
    public List<RunInfo> getRunInfos(
            @AuthenticationPrincipal
            JwtUserDetails userDetails,
            @RequestParam
            @EnumValid(enumClass = RunningInfoFilter.class, message = "유효하지 않은 필터입니다.", ignoreCase = true)
            String filteredBy,
            @RequestParam
            @EnumValid(enumClass = RunningMode.class, message = "유효하지 않은 러닝모드입니다.", ignoreCase = true)
            String runningMode,
            @RequestParam Long startEpoch, @RequestParam Long endEpoch,
            @RequestParam(required = false) Long cursorRunningId,
            @RequestParam(required = false) Long cursorStartedAt,
            @RequestParam(required = false) String cursorCourseName) {
        String memberUuid = userDetails.getUserId();
        return runningQueryService.findRunnings(
                runningMode, filteredBy,
                startEpoch, endEpoch,
                cursorStartedAt,
                cursorCourseName,
                cursorRunningId, memberUuid);
    }

}
