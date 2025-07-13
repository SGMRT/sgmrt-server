package soma.ghostrunner.domain.auth;

import org.springframework.security.core.AuthenticationException;

public interface AuthIdResolver {
    /**
     * 외부 인증 서비스의 토큰을 해석하여 토큰의 고유한 인증 ID를 반환한다
     *
     * @param externalAuthToken 외부 인증 시스템으로부터 받은 토큰
     * @return 주어진 토큰에 매핑되는 인증 UID
     * @throws AuthenticationException 토큰이 유효하지 않거나 ID를 추출할 수 없을 때
     */
    String resolveAuthId(String externalAuthToken);
}
