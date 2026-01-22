package soma.ghostrunner.domain.pacemaker.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.pacemaker.api.dto.request.CreatePacemakerRequest;
import soma.ghostrunner.domain.pacemaker.api.dto.request.PacemakerPatchAfterRunningRequest;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerInCourseViewPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.pacemaker.api.support.PacemakerApiMapper;
import soma.ghostrunner.domain.pacemaker.application.PacemakerService;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

@RestController
@RequiredArgsConstructor
public class PacemakerApi {

    private final PacemakerApiMapper mapper;

    private final PacemakerService pacemakerService;

    @PostMapping("/v1/pacemaker")
    public Long createPacemaker(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestBody @Valid CreatePacemakerRequest request) {
        String memberUuid = userDetails.getUserId();
        return pacemakerService.createPaceMaker(memberUuid, mapper.toCommand(request));
    }

    @GetMapping("/v1/pacemaker/{pacemakerId}")
    public PacemakerPollingResponse getPacemaker(
            @AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long pacemakerId) {
        String memberUuid = userDetails.getUserId();
        return pacemakerService.getPacemaker(pacemakerId, memberUuid);
    }

    @DeleteMapping("/v1/pacemaker/{pacemakerId}")
    public void deletePacemaker(@AuthenticationPrincipal JwtUserDetails userDetails, @PathVariable Long pacemakerId) {
        String memberUuid = userDetails.getUserId();
        pacemakerService.deletePacemaker(memberUuid, pacemakerId);
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
        return pacemakerService.getRateLimitCounter(memberUuid);
    }

}
