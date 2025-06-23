package soma.ghostrunner.domain.running.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.request.CreateCourseAndRunRequest;
import soma.ghostrunner.domain.running.api.dto.request.RunOnCourseRequest;
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
        return runningCommandService.createCourseAndRun(mapper.toCommand(req), req.getCourseName(), 1L);
    }

    @PostMapping("v1/runs/{courseId}/{memberId}")
    public Long runExistingCourse(@RequestBody @Valid RunOnCourseRequest req,
                                  @PathVariable Long courseId, @PathVariable Long memberId) {
        return runningCommandService.createRun(mapper.toCommand(req), courseId, memberId);
    }
}
