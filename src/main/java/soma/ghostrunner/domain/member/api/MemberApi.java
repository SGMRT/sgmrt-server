package soma.ghostrunner.domain.member.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.api.dto.request.MemberSettingsUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.ProfileImageUploadRequest;
import soma.ghostrunner.domain.member.api.dto.response.MemberResponse;
import soma.ghostrunner.domain.member.application.MemberService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberApi {

    private final MemberService memberService;

    @GetMapping("/{memberUuid}")
    public MemberResponse getMember(@PathVariable("memberUuid") String memberUuid) {
        // todo 본인만 확인 가능
        return memberService.findMemberDtoByUuid(memberUuid);
    }

    @PatchMapping("/{memberUuid}")
    public void updateMember(
            @PathVariable("memberUuid") String memberUuid,
            @Valid @RequestBody MemberUpdateRequest memberUpdateRequest) {
        // todo 본인만 수정 가능
        memberService.updateMember(memberUuid, memberUpdateRequest);
    }

    @PostMapping("/{memberUuid}/terms-agreement")
    public void renewTermsAgreement(
            @PathVariable("memberUuid") String memberUuid,
            @Valid @RequestBody TermsAgreementDto termsAgreementDto) {
        // todo 본인만 수정 가능
        memberService.saveTermsAgreement(memberUuid, termsAgreementDto);
    }

    @PostMapping("/{memberUuid}/profile-image/upload-url")
    public String generateProfileImageUploadUrl(
            @PathVariable("memberUuid") String memberUuid,
            @RequestBody @Valid ProfileImageUploadRequest request) {
        return memberService.generateProfileImageUploadUrl(memberUuid, request);

    }

    @PatchMapping("/{memberUuid}/settings")
    public void updateMemberSettings(
            @PathVariable("memberUuid") String memberUuid,
            @Valid @RequestBody MemberSettingsUpdateRequest request) {
        memberService.updateMemberSettings(memberUuid, request);
    }

    @DeleteMapping("/{memberUuid}")
    public void deleteMember(@PathVariable("memberUuid") String memberUuid) {
        // todo 본인만 수정 가능
        memberService.deactivateAccount(memberUuid);
    }

}
