package soma.ghostrunner.domain.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import soma.ghostrunner.domain.member.MemberService;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberNotFoundException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final AuthIdResolver authIdResolver;

    public Object signIn(String authorizationHeader) {
        String firebaseUid = resolveAuthIdOrThrow(authorizationHeader);
        try {
            Member member = memberService.findMemberByAuthUid(firebaseUid);
            // todo access token & refresh token 반환

            return null;
        } catch (MemberNotFoundException e) {
            throw new AuthenticationServiceException("존재하지 않는 사용자");
        }
    }


    private String resolveAuthIdOrThrow(String authorizationHeader) {
        String authToken = extractAuthTokenOrThrow(authorizationHeader);
        return authIdResolver.resolveAuthId(authToken);
    }

    private String extractAuthTokenOrThrow(String authorizationHeader) {
        if(!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty authorization header");

        String token = authorizationHeader.substring("Bearer ".length());
        if(!StringUtils.hasText(token))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty token");

        return token;
    }

}
