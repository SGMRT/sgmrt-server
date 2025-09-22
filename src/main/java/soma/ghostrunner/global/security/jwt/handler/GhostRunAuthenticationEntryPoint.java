package soma.ghostrunner.global.security.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.ErrorResponse;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GhostRunAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String AUTH_EXCEPTION_ATTRIBUTE = "authentication";
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode authenticationErrorCode = (ErrorCode) request.getAttribute(AUTH_EXCEPTION_ATTRIBUTE);
        ErrorResponse errorResponse = ErrorResponse.of(authenticationErrorCode);
        setErrorInfoToResponse(response, authenticationErrorCode);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private void setErrorInfoToResponse(HttpServletResponse response, ErrorCode authenticationErrorCode) {
        response.setStatus(authenticationErrorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
    }

}
