package soma.ghostrunner.global.security.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.ErrorResponse;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GhostRunAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ErrorCode authorizationErrorCode = ErrorCode.ACCESS_DENIED;
        ErrorResponse errorResponse = ErrorResponse.of(authorizationErrorCode);
        setErrorInfoToResponse(response, authorizationErrorCode);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private void setErrorInfoToResponse(HttpServletResponse response, ErrorCode authorizationErrorCode) {
        response.setStatus(authorizationErrorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
    }

}
