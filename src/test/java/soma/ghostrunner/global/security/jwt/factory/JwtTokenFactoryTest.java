package soma.ghostrunner.global.security.jwt.factory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.auth.application.dto.JwtTokens;

import javax.crypto.SecretKey;
import java.util.UUID;

class JwtTokenFactoryTest {

    private JwtTokenFactory jwtTokenFactory;
    private final String testSecretKey = "eb3bf053b5b883f41321e6164f50b9c62f44c9382858feb5e8598ccd0dadddcdf" +
            "16f3e8b93fa412f50801503cdf1bbfa2ac4e5e7d5706f47b97371821f5178e9";
    private final long accessTokenExpTime = 3600L;
    private final long refreshTokenExpTime = 86400L;

    @BeforeEach
    void setUp() {
        jwtTokenFactory = new JwtTokenFactory(testSecretKey, accessTokenExpTime, refreshTokenExpTime);
    }

    @DisplayName("액세스/리프레쉬 토큰을 생성한다.")
    @Test
    void createTokens() {
        // given
        String userId = UUID.randomUUID().toString();

        // when
        JwtTokens jwtTokens = jwtTokenFactory.createTokens(userId);

        // then
        Assertions.assertThat(parseToken(jwtTokens.accessToken()).get("userId")).isEqualTo(userId);
        Assertions.assertThat(parseToken(jwtTokens.refreshToken()).get("userId")).isEqualTo(userId);
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(testSecretKey));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

}
