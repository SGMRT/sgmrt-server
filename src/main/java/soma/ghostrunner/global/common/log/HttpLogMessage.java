package soma.ghostrunner.global.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class HttpLogMessage {

    private static final List<String> LOGGABLE_URIS = List.of("/v1/runs", "/v1/courses", "/v1/members", "/v1/auth");
    private static final List<String> LOGGABLE_HEADERS = List.of("Host", "X-Forwarded-For", "Origin", "Content-Type", "Referer", "User-Agent");
    private static final int MAX_BODY_SIZE = 4096;

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String TRUNCATED_MESSAGE = "... (truncated)";
    private static final String EMPTY_BODY_MESSAGE = "EMPTY";
    private static final String CANNOT_LOGGABLE_URI_MESSAGE = "This uri request cannot log..";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String httpMethod;
    private final String requestUri;
    private final String httpStatus;
    private final Double elapsedTime;
    private final String clientIp;
    private final String memberUuid;
    private final Map<String, String> headers;
    private final String queryParameters;
    private final String requestBody;
    private final String responseBody;

    @Builder(access = AccessLevel.PRIVATE)
    private HttpLogMessage(String httpMethod, String requestUri, String httpStatus, Double elapsedTime,
                           String clientIp, String memberUuid, Map<String, String> headers,
                           String queryParameters, String requestBody, String responseBody) {
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.httpStatus = httpStatus;
        this.elapsedTime = elapsedTime;
        this.clientIp = clientIp;
        this.memberUuid = memberUuid;
        this.headers = headers;
        this.queryParameters = queryParameters;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
    }

    public static HttpLogMessage of(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, Double elapsedTime) {
        String requestUri = request.getRequestURI();
        boolean isLoggableUri = isLoggableUri(requestUri);

        String reqBody = isLoggableUri ? truncateBody(request.getContentAsByteArray()) : CANNOT_LOGGABLE_URI_MESSAGE;
        String resBody = isLoggableUri ? truncateBody(response.getContentAsByteArray()) : CANNOT_LOGGABLE_URI_MESSAGE;

        return HttpLogMessage.builder()
                .httpMethod(request.getMethod())
                .requestUri(requestUri)
                .httpStatus(String.valueOf(HttpStatus.valueOf(response.getStatus())))
                .elapsedTime(elapsedTime)
                .clientIp(extractClientIp(request))
                .memberUuid("FAKE_UUID") // TODO: 실제 멤버 UUID 추출 로직 필요
                .headers(extractHeaders(request))
                .queryParameters(request.getQueryString())
                .requestBody(reqBody)
                .responseBody(resBody)
                .build();
    }

    private static String extractClientIp(ContentCachingRequestWrapper request) {
        String ip = request.getHeader(X_FORWARDED_FOR);
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }

    private static Map<String, String> extractHeaders(ContentCachingRequestWrapper request) {
        Map<String, String> result = new HashMap<>();
        LOGGABLE_HEADERS.forEach(lh -> {
                    if (request.getHeader(lh) != null) {
                        result.put(lh, request.getHeader(lh));
                    } else {
                        result.put(lh, EMPTY_BODY_MESSAGE);
                    }
                });
        return result;
    }

    private static boolean isLoggableUri(String requestUri) {
        return LOGGABLE_URIS.stream().anyMatch(requestUri::startsWith);
    }

    private static String truncateBody(byte[] body) {
        if (body == null || body.length == 0) {
            return EMPTY_BODY_MESSAGE;
        }
        int length = Math.min(body.length, MAX_BODY_SIZE);
        String bodyStr = new String(body, 0, length, StandardCharsets.UTF_8);

        return body.length > MAX_BODY_SIZE ? bodyStr + TRUNCATED_MESSAGE : bodyStr;
    }

    public String toPrettierLog() {
        String prettierHeaders = toPrettierJson(headers, headers.toString());
        String prettierReqBody = toPrettierJson(requestBody, requestBody);
        String prettierResBody = toPrettierJson(responseBody, responseBody);

        String logFormat = """
                |
                |[REQUEST] %s %s %s (%.3fs)
                |>> CLIENT_IP: %s
                |>> MEMBER_UUID: %s
                |>> HEADERS: %s
                |>> QUERY PARAMETERS: %s
                |>> REQUEST_BODY: %s
                |>> RESPONSE_BODY: %s
                """;

        String log = String.format(logFormat, httpMethod, requestUri, httpStatus, elapsedTime, clientIp, memberUuid,
                prettierHeaders, queryParameters, prettierReqBody, prettierResBody);

        return trimMargin(log);
    }

    private String toPrettierJson(Object object, String originalText) {
        if (object instanceof String s && (s.equals(CANNOT_LOGGABLE_URI_MESSAGE) || s.equals(EMPTY_BODY_MESSAGE))) {
            return s;
        }
        try {
            if (object instanceof String s) {
                Object jsonObject = objectMapper.readValue(s, Object.class);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            log.warn("JSON 파싱 또는 변환에 실패했습니다. 원본 데이터를 반환합니다. Error: {}", e.getMessage());
            return originalText;
        }
    }

    private String trimMargin(String text) {
        return text.lines()
                .map(line -> line.replaceFirst("^\\s*\\|", ""))
                .collect(Collectors.joining("\n"));
    }

}
