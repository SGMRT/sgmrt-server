package soma.ghostrunner.global.config;

import io.sentry.SentryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Configuration
public class SentryConfig {

    // API 요청 접두어 (~/v1, ~/v2, ...)
    private static final Pattern API_PREFIX_PATTERN = Pattern.compile("^/v\\d+/.*");
    // API 요청 외에 Trace 기록을 허용할 엔드포인트 접두어
    private static final List<String> WHITELISTED_STATIC_PREFIX_ENDPOINTS = List.of(); // "/static/", "/public/" 등등

    /** 화이트리스트 엔드포인트 요청만 Sentry로 전송한다. */
    @Bean
    public SentryOptions.BeforeSendTransactionCallback beforeSendTransactionCallback() {
        return (transaction, hint) -> {
            try {
                String uri = Objects.requireNonNull(transaction.getTransaction()).split(" ")[1];
                if (isVersionedApiRequest(uri) || isStaticRequest(uri)) {
                    return transaction;
                }
            } catch (Exception e) {
                log.error("[Sentry] Error in beforeSendTransactionCallback", e);
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
