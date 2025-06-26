package soma.ghostrunner.domain.running.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.CreateRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.UpdateRunNameRequest;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.RunningCommandService;

@RestController
@RequiredArgsConstructor
public class RunningApi {

    private final RunningCommandService runningCommandService;
    private final RunningApiMapper mapper;

    @GetMapping("")
    public String hello() {
        return "Hello World!";
    }

    @PostMapping("v1/runs/{memberId}")
    public CreateCourseAndRunResponse createCourseAndRun(@RequestBody @Valid CreateCourseAndRunRequest req, @PathVariable Long memberId) {
        return runningCommandService.createCourseAndRun(mapper.toCommand(req), memberId);
    }

    @PostMapping("v1/runs/{courseId}/{memberId}")
    public Long createRun(@RequestBody @Valid CreateRunRequest req, @PathVariable Long courseId, @PathVariable Long memberId) {
        return runningCommandService.createRun(mapper.toCommand(req), courseId, memberId);
    }

    @PatchMapping("v1/runs/{runningId}/name/{memberId}")
    public void updateRunningName(@RequestBody @Valid UpdateRunNameRequest req, @PathVariable Long runningId, @PathVariable Long memberId) {
        runningCommandService.updateRunningName(req.getName(), memberId, runningId);
    }
}
