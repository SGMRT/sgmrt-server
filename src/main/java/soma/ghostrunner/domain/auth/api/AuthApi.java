package soma.ghostrunner.domain.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.auth.api.dto.request.SignUpRequest;
import soma.ghostrunner.domain.auth.api.dto.response.AuthenticationResponse;
import soma.ghostrunner.domain.auth.application.AuthService;
import soma.ghostrunner.domain.auth.exception.InvalidTokenException;
import soma.ghostrunner.global.error.ErrorCode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthApi {

    private final AuthService authService;

    @PostMapping("/firebase-signin")
    public AuthenticationResponse firebaseSignIn(@RequestHeader("Authorization") String authorizationHeader) {
        String firebaseToken = extractToken(authorizationHeader);
        return authService.signIn(firebaseToken);
    }

    @PostMapping("/firebase-signup")
    public AuthenticationResponse firebaseSignUp (
            @RequestHeader("Authorization") String authorizationHeader, @Valid @RequestBody SignUpRequest signUpRequest) {
        String firebaseToken = extractToken(authorizationHeader);
        return authService.signUp(firebaseToken, signUpRequest);
    }

    @PostMapping("/reissue")
    public AuthenticationResponse reissue(@RequestHeader("Authorization") String authorizationHeader) {
        String refreshToken = extractToken(authorizationHeader);
        return authService.reissueTokens(refreshToken);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String authorizationHeader) {
        String refreshToken = extractToken(authorizationHeader);
        authService.logout(refreshToken);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Bearer 토큰입니다.");
        }
        return authorizationHeader.substring(7);
    }

}
