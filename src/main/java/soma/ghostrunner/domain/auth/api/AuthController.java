package soma.ghostrunner.domain.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.response.SignInResponse;
import soma.ghostrunner.domain.auth.api.dto.response.SignUpResponse;
import soma.ghostrunner.domain.auth.application.AuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/firebase-sign-in")
    public SignInResponse firebaseSignIn(
            @RequestHeader("Authorization") String authorizationHeader) {
        return authService.signIn(authorizationHeader);
    }


    @PostMapping("/firebase-sign-up")
    public SignUpResponse firebaseSignUp(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody SignUpRequest signUpRequest) {
        return authService.signUp(authorizationHeader, signUpRequest);
    }


    // 토큰 갱신


    // (선택) 로그아웃


}
