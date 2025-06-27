package soma.ghostrunner.domain.running.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.CreateRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.UpdateRunNameRequest;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunInfo;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RunningApi {

    private final RunningQueryService runningQueryService;
    private final RunningCommandService runningCommandService;
    private final RunningApiMapper mapper;

    @PostMapping("/v1/runs/{memberId}")
    public CreateCourseAndRunResponse createCourseAndRun(@RequestBody @Valid CreateCourseAndRunRequest req, @PathVariable Long memberId) {
        return runningCommandService.createCourseAndRun(mapper.toCommand(req), memberId);
    }

    @PostMapping("/v1/runs/{courseId}/{memberId}")
    public Long createRun(@RequestBody @Valid CreateRunRequest req, @PathVariable Long courseId, @PathVariable Long memberId) {
        return runningCommandService.createRun(mapper.toCommand(req), courseId, memberId);
    }

    @PatchMapping("/v1/runs/{runningId}/name/{memberId}")
    public void patchRunningName(@RequestBody @Valid UpdateRunNameRequest req, @PathVariable Long runningId, @PathVariable Long memberId) {
        runningCommandService.updateRunningName(req.getName(), memberId, runningId);
    }

    @GetMapping("/v1/runs/{runningId}/telemetries")
    public List<TelemetryDto> getTelemetries(@PathVariable Long runningId) {
        return runningQueryService.findTelemetriesById(runningId);
    }

    @GetMapping("/v1/runs/{runningId}")
    public SoloRunInfo getSoloRunInfo(@PathVariable Long runningId) {
        return runningQueryService.findSoloRunInfoById(runningId);
    }
}
