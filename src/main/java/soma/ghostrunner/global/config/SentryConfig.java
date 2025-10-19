package soma.ghostrunner.global.config;

import io.sentry.SentryOptions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
public class SentryConfig {

    // API 요청 접두어 (~/v1, ~/v2, ...)
    private static final Pattern API_PREFIX_PATTERN = Pattern.compile("^/v\\d+/.*");
    // 기타 Trace 기록을 허용할 엔드포인트 접두어
    private static final List<String> WHITELISTED_STATIC_PREFIX_ENDPOINTS = Arrays.asList(
            "/swagger-ui/",
            "/v3/api-docs/"
    );

    /** 화이트리스트 엔드포인트 요청만 Sentry로 전송한다. */
    @Bean
    public SentryOptions.BeforeSendTransactionCallback beforeSendTransactionCallback() {
        return (transaction, hint) -> {

            Object requestObject = hint.get("javax.servlet.http.HttpServletRequest");

            if (requestObject instanceof HttpServletRequest request) {
                String uri = request.getRequestURI();

                if (isVersionedApiRequest(uri)) {
                    return transaction;
                }

                if (isStaticRequest(uri)) {
                    return transaction;
                }
            }
            
            return null;
        };
    }

    private boolean isVersionedApiRequest(String uri) {
        if (uri == null) {
            return false;
        }
        return API_PREFIX_PATTERN.matcher(uri).matches();
    }

    private boolean isStaticRequest(String uri) {
        if (uri == null) {
            return false;
        }
        return WHITELISTED_STATIC_PREFIX_ENDPOINTS.stream().anyMatch(uri::startsWith);
    }
}
