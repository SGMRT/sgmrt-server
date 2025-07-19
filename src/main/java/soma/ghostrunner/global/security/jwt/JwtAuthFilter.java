package soma.ghostrunner.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.logging.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import soma.ghostrunner.global.security.exception.ParsingTokenException;
import soma.ghostrunner.global.security.jwt.support.JwtProvider;

import java.io.IOException;
import java.util.List;

import static soma.ghostrunner.global.error.ErrorCode.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final String AUTH_EXCEPTION_ATTRIBUTE = "authentication";

    private static final List<String> PERMIT_URLS = List.of(
            "/v1/auth", "/swagger"
    );

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (hasPermittedUris(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = jwtProvider.extractTokenFromHeader(request);
            Claims claims = jwtProvider.parseClaims(token);

            String userId = jwtProvider.getUserId(claims);
            MDC.put("userId", userId);

            JwtUserDetails userDetails = new JwtUserDetails(userId);
            UsernamePasswordAuthenticationToken authentication = createAuthenticationToken(userDetails);
            setAuthentication(authentication);
        }
        catch (ExpiredJwtException e) {
            log.info("Token has expired: {}", e.getMessage());
            request.setAttribute(AUTH_EXCEPTION_ATTRIBUTE, EXPIRED_TOKEN);
        }
        catch (JwtException | ParsingTokenException e) {
            log.warn("Invalid JWT Token: {}", e.getMessage());
            request.setAttribute(AUTH_EXCEPTION_ATTRIBUTE, INVALID_TOKEN);
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasPermittedUris(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return PERMIT_URLS.stream()
                .anyMatch(requestURI::startsWith);
    }

    private void setAuthentication(UsernamePasswordAuthenticationToken authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private UsernamePasswordAuthenticationToken createAuthenticationToken(JwtUserDetails userDetails) {
        return new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
    }

}
