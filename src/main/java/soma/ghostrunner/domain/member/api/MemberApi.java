package soma.ghostrunner.domain.member.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.api.dto.request.MemberSettingsUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.response.MemberResponse;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberApi {

    private final MemberService memberService;

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @GetMapping("/{memberUuid}")
    public MemberResponse getMember(
            @PathVariable("memberUuid") String memberUuid,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return memberService.findMemberDtoByUuid(memberUuid);
    }

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PatchMapping("/{memberUuid}")
    public void updateMember(
            @PathVariable("memberUuid") String memberUuid,
            @Valid @RequestBody MemberUpdateRequest memberUpdateRequest,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        memberService.updateMember(memberUuid, memberUpdateRequest);
    }

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PostMapping("/{memberUuid}/terms-agreement")
    public void renewTermsAgreement(
            @PathVariable("memberUuid") String memberUuid,
            @Valid @RequestBody TermsAgreementDto termsAgreementDto,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        memberService.recordTermsAgreement(memberUuid, termsAgreementDto, LocalDateTime.now());
    }

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @PatchMapping("/{memberUuid}/settings")
    public void updateMemberSettings(
            @PathVariable("memberUuid") String memberUuid,
            @Valid @RequestBody MemberSettingsUpdateRequest request,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        memberService.updateMemberSettings(memberUuid, request);
    }

    @PreAuthorize("@authService.isOwner(#memberUuid, #userDetails)")
    @DeleteMapping("/{memberUuid}")
    public void deleteMember(
            @PathVariable("memberUuid") String memberUuid,
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        memberService.removeAccount(memberUuid);
    }

    @GetMapping("/vdot")
    public Integer getVdot(@AuthenticationPrincipal JwtUserDetails userDetails) {
        String memberUuid = userDetails.getUserId();
        return memberService.findMemberVdot(memberUuid);
    }

    @PostMapping("/vdot")
    public void postVdot(
            @AuthenticationPrincipal JwtUserDetails userDetails, @RequestParam RunningLevel level) {
        String memberUuid = userDetails.getUserId();
        memberService.calculateAndSaveVdot(memberUuid, level.getDisplayName());
    }
  
}
