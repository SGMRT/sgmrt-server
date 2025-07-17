package soma.ghostrunner.global.security.jwt.factory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.ZonedDateTime;
import java.util.Date;

@Component
public class JwtTokenFactory {

    private final Key key;
    private final long accessTokenExpTime;
    private final long refreshTokenExpTime;

    public JwtTokenFactory(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration_time.access_token}") long accessTokenExpTime,
            @Value("${jwt.expiration_time.refresh_token}") long refreshTokenExpTime
    ) {
        byte[] keyBytes = Decoders.BASE64URL.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpTime = accessTokenExpTime;
        this.refreshTokenExpTime = refreshTokenExpTime;
    }

    public String createAccessToken(String memberId) {
        Claims claims = createClaims(memberId);
        ZonedDateTime now = ZonedDateTime.now();
        return createToken(claims, now, now.plusSeconds(accessTokenExpTime));
    }

    public String createRefreshToken(String memberId){
        Claims claims = createClaims(memberId);
        ZonedDateTime now = ZonedDateTime.now();

        return createToken(claims, now, now.plusSeconds(refreshTokenExpTime));
    }

    private Claims createClaims(String memberId) {
        Claims claims = Jwts.claims();
        claims.put("userId", memberId);
        return claims;
    }

    private String createToken(Claims claims, ZonedDateTime now, ZonedDateTime tokenValidity) {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(Date.from(now.toInstant()))
                .setExpiration(Date.from(tokenValidity.toInstant()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

}
