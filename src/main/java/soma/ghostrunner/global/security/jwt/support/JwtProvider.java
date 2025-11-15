package soma.ghostrunner.global.security.jwt.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import soma.ghostrunner.global.security.exception.ParsingTokenException;

import java.security.Key;

@Component
public class JwtProvider {

    private final Key key;

    public JwtProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64URL.decode(secretKey);
        key = Keys.hmacShaKeyFor(keyBytes);
    }
  
    public String extractTokenFromHeader(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if ( authorizationHeader == null || !authorizationHeader.startsWith("Bearer ") || authorizationHeader.isBlank() ) {
            throw new ParsingTokenException("HTTP 요청 헤더에서 토큰 파싱을 실패했습니다.");
        }
        return authorizationHeader.substring(7);
    }

    public String getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return getUserId(claims);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key).build()
                .parseClaimsJws(token)
                .getBody();
    }

    private String getUserId(Claims claims) {
        return claims.get("userId", String.class);
    }

}
