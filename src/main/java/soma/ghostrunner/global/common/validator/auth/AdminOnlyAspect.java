package soma.ghostrunner.global.common.validator.auth;


import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.auth.application.AuthService;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.AuthException;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

@Aspect
@Component
@RequiredArgsConstructor
public class AdminOnlyAspect {

    private final AuthService authService;

    @Before("@annotati" +
            "on(soma.ghostrunner.global.common.validator.auth.AdminOnly) || @within(soma.ghostrunner.global.common.validator.auth.AdminOnly)")
    void checkAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException(ErrorCode.ACCESS_DENIED, "관리자 전용 API입니다.");
        }

        Object principal = authentication.getPrincipal();
        if ("anonymousUser".equals(principal)) {
            throw new AuthException(ErrorCode.ACCESS_DENIED, "관리자 전용 API입니다.");
        }

        JwtUserDetails userDetails = (JwtUserDetails) principal;
        if (!authService.isAdmin(userDetails.getUserId())) {
            throw new AuthException(ErrorCode.ACCESS_DENIED, "관리자 전용 API입니다.");
        }
    }

}
