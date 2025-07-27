package soma.ghostrunner.domain.member.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.member.api.dto.TermsAgreementDto;
import soma.ghostrunner.domain.member.api.dto.request.MemberSettingsUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.MemberUpdateRequest;
import soma.ghostrunner.domain.member.api.dto.request.ProfileImageUploadRequest;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberApi {

    private final MemberService memberService;
    private final MemberMapper memberMapper;

    @GetMapping("/{memberUuid}")
    public Object getMember(@PathVariable("memberUuid") String memberUuid) {
        // todo 본인만 확인 가능
        return memberMapper.toMemberResponse(memberService.findMemberByUuid(memberUuid));
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

}
