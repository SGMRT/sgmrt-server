package soma.ghostrunner.global.common.log;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Request 를 로깅하는 Logger
 *
 * @author ijin
 */
@Slf4j
@Component
public class HttpLogger {

    // 로깅
    public void log(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, double elapsedTime) {
        StringBuffer logBuffer = new StringBuffer();

        // Request 메타데이터 로깅
        logBuffer.append("\n");
        logBuffer.append("[Request] " + request.getMethod() + "  " + request.getRequestURI() + "  " + parseResponseStatus(response) + "  " + elapsedTime).append("s\n");
        logBuffer.append(">> Client IP: " + request.getRemoteAddr()).append("\n");
        logBuffer.append(">> Headers: ").append(parseRequestHeaders(request)).append("\n");

        // 쿼리 스트링
        String queryString = request.getQueryString();
        logBuffer.append(">> Request Param: ").append(queryString != null ? queryString : "None").append("\n");

        // Request Body
        logBuffer.append(">> Request Body: ").append("\n").append(parseRequestBody(request)).append("\n");

        // Response Body
        logBuffer.append(">> Response Body: ").append("\n").append(parseResponseBody(response)).append("\n");

        // 로그
        log.info(logBuffer.toString());
    }

    // 응답코드 파싱
    private String parseResponseStatus(HttpServletResponse response) {
        HttpStatus responseStatus = HttpStatus.valueOf(response.getStatus());
        return String.valueOf(responseStatus.value());
    }

    // 헤더 매핑
    private Map<String, Object> parseRequestHeaders(ContentCachingRequestWrapper request) {
        Map<String, Object> headerMap = new HashMap<>();

        Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            String headerName = headers.nextElement();
            headerMap.put(headerName, request.getHeader(headerName));
        }
        return headerMap;
    }

    // Request Body 파싱
    private String parseRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        return content.length == 0 ? "Empty Body" : new String(content, StandardCharsets.UTF_8);
    }

    // Response Body 파싱
    private String parseResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        return content.length == 0 ? "Empty Body" : new String(content, StandardCharsets.UTF_8);
    }

}
