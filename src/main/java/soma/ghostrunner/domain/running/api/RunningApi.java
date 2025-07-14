package soma.ghostrunner.domain.running.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
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
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

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
  
    @PostMapping("/v1/runs/{memberId}")
    public CreateCourseAndRunResponse createCourseAndRun(@RequestBody @Valid CreateCourseAndRunRequest req, @PathVariable Long memberId) {
        return runningCommandService.createCourseAndRun(mapper.toCommand(req), memberId);
    }

    @PostMapping("/v1/runs/courses/{courseId}/{memberId}")
    public Long createRun(@RequestBody @Valid CreateRunRequest req, @PathVariable Long courseId, @PathVariable Long memberId) {
        return runningCommandService.createRun(mapper.toCommand(req), courseId, memberId);
    }

    @PatchMapping("/v1/runs/{runningId}/name/{memberId}")
    public void patchRunningName(@RequestBody @Valid UpdateRunNameRequest req, @PathVariable Long runningId, @PathVariable Long memberId) {
        runningCommandService.updateRunningName(req.getName(), memberId, runningId);
    }

    @GetMapping("/v1/runs/{runningId}/telemetries")
    public List<TelemetryDto> getRunningTelemetries(@PathVariable Long runningId) {
        return runningQueryService.findRunningTelemetries(runningId);
    }

    @GetMapping("/v1/runs/{runningId}")
    public SoloRunDetailInfo getSoloRunInfo(@PathVariable Long runningId) {
        return runningQueryService.findSoloRunInfo(runningId);
    }

    @GetMapping("/v1/runs/{myRunningId}/ghosts/{ghostRunningId}")
    public GhostRunDetailInfo getGhostRunInfo(@PathVariable Long myRunningId, @PathVariable Long ghostRunningId) {
        return runningQueryService.findGhostRunInfo(myRunningId, ghostRunningId);
    }

    @PatchMapping("/v1/runs/{runningId}/isPublic")
    public void patchRunningPublicStatus(@PathVariable Long runningId) {
        runningCommandService.updateRunningPublicStatus(runningId);
    }

    @DeleteMapping("/v1/runs")
    public void deleteRunnings(@RequestBody @Valid DeleteRunningRequest request) {
        runningCommandService.deleteRunnings(request.getRunningIds());
    }

    @GetMapping("/v1/runs")
    public List<RunInfo> getRunInfos(@RequestParam(required = false) Long cursorStartedAt,
                                     @RequestParam(required = false) Long cursorRunningId) {
        return runningQueryService.findRunnings(cursorStartedAt, cursorRunningId);
    }

}
