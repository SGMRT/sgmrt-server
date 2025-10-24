package soma.ghostrunner.domain.running.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.running.api.dto.request.CreatePacemakerRequest;
import soma.ghostrunner.domain.running.api.dto.request.PacemakerPatchAfterRunningRequest;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerInCourseViewPollingResponse;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.PacemakerService;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

@RestController
@RequiredArgsConstructor
public class PacemakerApi {

    private final RunningApiMapper mapper;

    private final PacemakerService paceMakerService;
    private final PacemakerService pacemakerService;

    @PostMapping("/v1/pacemaker")
    public Long createPacemaker(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestBody @Valid CreatePacemakerRequest request) throws InterruptedException {
        String memberUuid = userDetails.getUserId();
        return paceMakerService.createPaceMaker(memberUuid, mapper.toCommand(request));
    }

    @GetMapping("/v1/pacemaker/{pacemakerId}")
    public PacemakerPollingResponse getPacemaker(
            @AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long pacemakerId) {
        String memberUuid = userDetails.getUserId();
        return paceMakerService.getPacemaker(pacemakerId, memberUuid);
    }

    @DeleteMapping("/v1/pacemaker/{pacemakerId}")
    public void deletePacemaker(@AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long pacemakerId) {
        String memberUuid = userDetails.getUserId();
        paceMakerService.deletePacemaker(memberUuid, pacemakerId);
    }

    @GetMapping("/v1/pacemaker")
    public PacemakerInCourseViewPollingResponse getPacemakerInCourseView(
            @AuthenticationPrincipal JwtUserDetails userDetails, @RequestParam Long courseId) {
        String memberUuid = userDetails.getUserId();
        return pacemakerService.getPacemakerInCourse(memberUuid, courseId);
    }

    @PatchMapping("/v1/pacemaker/after-running")
    public void patchPacemakerAfterRunning(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestBody @Valid PacemakerPatchAfterRunningRequest req) {
        String memberUuid = userDetails.getUserId();
        pacemakerService.updateAfterRunning(memberUuid, req.getPacemakerId(), req.getRunningId());
    }

    @GetMapping("/v1/pacemaker/rate-limit")
    public Long getRateLimitCounterToMakePacemaker(@AuthenticationPrincipal JwtUserDetails userDetails) {
        String memberUuid = userDetails.getUserId();
        return paceMakerService.getRateLimitCounter(memberUuid);
    }

}
