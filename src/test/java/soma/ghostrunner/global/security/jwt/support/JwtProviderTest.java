package soma.ghostrunner.global.security.jwt.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import soma.ghostrunner.global.security.exception.ParsingTokenException;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private final String testSecretKey = "eb3bf053b5b883f41321e6164f50b9c62f44c9382858feb5e8598ccd0dadddcdf" +
            "16f3e8b93fa412f50801503cdf1bbfa2ac4e5e7d5706f47b97371821f5178e9";
    private final SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(testSecretKey));
    private final String testMemberId = "testUser123";
    private String validToken;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(testSecretKey);
        validToken = Jwts.builder()
                .claim("userId", testMemberId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600 * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("유효한 토큰의 클레임을 파싱한다")
    void parseClaims_success() {
        // when
        Claims claims = jwtProvider.parseClaims(validToken);

        // then
        assertThat(claims.get("userId", String.class)).isEqualTo(testMemberId);
    }

    @Test
    @DisplayName("만료된 토큰을 파싱하면 ExpiredJwtException 예외가 발생한다")
    void parseClaims_withExpiredToken() {
        // given
        String expiredToken = Jwts.builder()
                .claim("userId", testMemberId)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // when // then
        assertThatThrownBy(
                () -> jwtProvider.parseClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("잘못된 키로 서명된 토큰을 파싱하면 SignatureException 예외가 발생한다")
    void parseClaims_withInvalidSignature() {
        // given
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                Decoders.BASE64URL.decode("thisIsA_completelyDifferentWrongTestKeyForTesting"));
        String invalidSignatureToken = Jwts.builder()
                .claim("userId", testMemberId)
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        // when // then
        assertThatThrownBy(() -> jwtProvider.parseClaims(invalidSignatureToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("올바른 Authorization 헤더에서 토큰을 추출한다")
    void extractTokenFromHeader_success() {
        // given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("Authorization")).willReturn("Bearer " + validToken);

        // when
        String extractedToken = jwtProvider.extractTokenFromHeader(request);

        // then
        assertThat(extractedToken).isEqualTo(validToken);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 ParsingTokenException 예외가 발생한다")
    void extractTokenFromHeader_withNullHeader() {
        // given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        // when // then
        assertThatThrownBy(() -> jwtProvider.extractTokenFromHeader(request))
                .isInstanceOf(ParsingTokenException.class)
                .hasMessage("HTTP 요청 헤더에서 토큰 파싱을 실패했습니다.");
    }

    @Test
    @DisplayName("Bearer 접두사가 없으면 예외가 발생한다")
    void extractTokenFromHeader_withoutBearer() {
        // given
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("Authorization")).willReturn(validToken);

        // when // then
        assertThatThrownBy(() -> jwtProvider.extractTokenFromHeader(request))
                .isInstanceOf(ParsingTokenException.class);
    }

    @Test
    @DisplayName("클레임에서 userId를 가져온다")
    void getUserId() {
        // given
        Claims claims = jwtProvider.parseClaims(validToken);

        // when
        String userId = jwtProvider.getUserId(claims);

        // then
        assertThat(userId).isEqualTo(testMemberId);
    }

}
