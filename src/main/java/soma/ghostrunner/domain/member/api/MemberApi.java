package soma.ghostrunner.domain.member.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.member.api.dto.ProfileImageUploadRequest;
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
        return memberMapper.toMemberResponse(memberService.findMemberByUuid(memberUuid));
    }

    @PostMapping("/{memberUuid}/profile-image/upload-url")
    public String generateProfileImageUploadUrl(
            @PathVariable("memberUuid") String memberUuid,
            @RequestBody @Valid ProfileImageUploadRequest request) {
        return memberService.generateProfileImageUploadUrl(memberUuid, request);

    }



}
