package soma.ghostrunner.domain.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.response.SignInResponse;
import soma.ghostrunner.domain.auth.api.dto.response.SignUpResponse;
import soma.ghostrunner.domain.auth.application.AuthService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    // 로그인 -> fb id token 기반
    @PostMapping("/firebase-sign-in")
    public SignInResponse firebaseSignIn(
            @RequestHeader("Authorization") String authorizationHeader) {
        return authService.signIn(authorizationHeader);
    }


    // 회원가입 -> 입력한 정보를 받아서 회원 삽입
    @PostMapping("/firebase-sign-up")
    public SignUpResponse firebaseSignUp(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody SignUpRequest signUpRequest) {
        return authService.signUp(authorizationHeader, signUpRequest);
    }


    // 토큰 갱신


    // (선택) 로그아웃


}
