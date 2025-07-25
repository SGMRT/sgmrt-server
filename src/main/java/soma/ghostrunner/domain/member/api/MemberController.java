package soma.ghostrunner.domain.member.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.member.api.dto.ProfileImageUploadRequest;
import soma.ghostrunner.domain.member.application.MemberService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberController {

    private final MemberService memberService;

}
