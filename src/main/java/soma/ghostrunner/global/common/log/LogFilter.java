package soma.ghostrunner.global.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LogFilter extends OncePerRequestFilter {

    private final HttpLogger httpLogger;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = createRequestId();

        ContentCachingRequestWrapper cachingRequestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachingResponseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(cachingRequestWrapper, cachingResponseWrapper);
        } finally {
            long endTime = System.currentTimeMillis();
            double elapsedTime = (endTime - startTime) / 1000.0;

            httpLogger.log(cachingRequestWrapper, cachingResponseWrapper, elapsedTime);

            response.setHeader("X-Request-ID", requestId);
            MDC.clear();

            cachingResponseWrapper.copyBodyToResponse();
        }
    }

    private String createRequestId() {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        putValueToMdc("requestId", requestId);
        return requestId;
    }

    private void putValueToMdc(String key, String value) {
        MDC.put(key, value);
    }

}
