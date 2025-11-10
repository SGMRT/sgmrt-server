package soma.ghostrunner.global.config;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Profile("prod")
@Slf4j
@Configuration
public class SentryConfig {

    // API 요청 접두어 (~/v1, ~/v2, ...)
    private static final Pattern API_PREFIX_PATTERN = Pattern.compile("^/v\\d+/.*");
    // API 요청 외에 Trace 기록을 허용할 엔드포인트 접두어
    private static final List<String> WHITELISTED_STATIC_PREFIX_ENDPOINTS = List.of(); // "/static/", "/public/" 등등

    /**
     * 에러 이벤트 필터링: 4xx 에러 중 401, 403만 선택적으로 전송
     * beforeSend는 에러/예외 이벤트에만 적용됨
     */
    @Bean
    public SentryOptions.BeforeSendCallback beforeSendCallback() {
        return (event, hint) -> {
            try {

                // HTTP 상태 코드 확인
                if (event.getRequest() != null) {
                    String uri = event.getRequest().getUrl();

                    // API 엔드포인트가 아니면 필터링
                    if (uri != null && !isVersionedApiRequest(uri) && !isStaticRequest(uri)) {
                        log.debug("[Sentry] Filtering non-API error: {}", uri);
                        return null;
                    }
                }

                // 4xx 에러 필터링
                Throwable throwable = event.getThrowable();
                if (throwable != null) {
                    // ResponseStatusException 체크 (Spring WebFlux/MVC)
                    if (throwable.getClass().getSimpleName().contains("ResponseStatusException")) {
                        int statusCode = extractStatusCode(throwable);

                        if (statusCode >= 400 && statusCode < 500) {
                            // 401, 403만 WARNING 레벨로 전송
                            if (statusCode == 401 || statusCode == 403) {
                                event.setLevel(SentryLevel.WARNING);
                                log.debug("[Sentry] Sending 4xx error: {} - {}", statusCode, event.getRequest().getUrl());
                                return event;
                            }
                            // 나머지 4xx는 필터링
                            log.debug("[Sentry] Filtering 4xx error: {}", statusCode);
                            return null;
                        }
                    }

                    // 특정 예외 타입 필터링 (선택 사항)
                    String exceptionName = throwable.getClass().getSimpleName();
                    if (exceptionName.equals("AccessDeniedException") ||
                            exceptionName.equals("InvalidJwtException")) {
                        event.setLevel(SentryLevel.WARNING);
                    }
                }

                // 5xx 및 기타 에러는 그대로 전송
                return event;

            } catch (Exception e) {
                log.error("[Sentry] Error in beforeSendCallback", e);
                return event; // 필터링 실패 시 이벤트는 그대로 전송
            }
        };
    }

    /**
     * 트랜잭션(성능 추적) 필터링: 화이트리스트 엔드포인트만 전송
     * beforeSendTransaction은 트랜잭션 이벤트에만 적용됨
     */
    @Bean
    public SentryOptions.BeforeSendTransactionCallback beforeSendTransactionCallback() {
        return (transaction, hint) -> {
            try {

                String transactionName = transaction.getTransaction();
                if (transactionName == null) {
                    return null;
                }

                // "GET /v1/running/list" 형식에서 URI 추출
                String[] parts = transactionName.split(" ");
                if (parts.length < 2) {
                    return null;
                }

                String uri = parts[1];

                // 화이트리스트 체크
                if (isVersionedApiRequest(uri) || isStaticRequest(uri)) {
                    log.debug("[Sentry] Sending transaction: {}", transactionName);
                    return transaction;
                }

                log.debug("[Sentry] Filtering transaction: {}", transactionName);
                return null;

            } catch (Exception e) {
                log.error("[Sentry] Error in beforeSendTransactionCallback", e);
                return null; // 필터링 실패 시 트랜잭션은 전송 안 함
            }
        };
    }

    /**
     * ResponseStatusException에서 상태 코드 추출
     */
    private int extractStatusCode(Throwable throwable) {
        try {
            // Spring 6.x (Spring Boot 3.x)
            if (throwable instanceof org.springframework.web.server.ResponseStatusException) {
                return ((org.springframework.web.server.ResponseStatusException) throwable)
                        .getStatusCode().value();
            }
        } catch (Exception e) {
            log.debug("[Sentry] Failed to extract status code", e);
        }
        return 500; // 기본값
    }

    /**
     * 버전이 포함된 API 요청인지 확인 (/v1/*, /v2/* 등)
     */
    private boolean isVersionedApiRequest(String uri) {
        if (uri == null) {
            return false;
        }
        return API_PREFIX_PATTERN.matcher(uri).matches();
    }

    /**
     * 정적 리소스 요청인지 확인
     */
    private boolean isStaticRequest(String uri) {
        if (uri == null) {
            return false;
        }
        return WHITELISTED_STATIC_PREFIX_ENDPOINTS.stream().anyMatch(uri::startsWith);
    }
}

