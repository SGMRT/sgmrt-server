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

    // HTTP 요청, 응답 캐싱
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // MDC RequestID 설정
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        // 요청&응답 캐싱
        ContentCachingRequestWrapper cachingRequestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachingResponseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();        // 시작 시간
        filterChain.doFilter(cachingRequestWrapper, cachingResponseWrapper);
        long endTime = System.currentTimeMillis();          // 끝난 시간

        // 로그
        httpLogger.log(cachingRequestWrapper, cachingResponseWrapper, (endTime-startTime) / 1000.0);

        // MDC 해제
        MDC.remove("requestId");

        // 캐싱한 응답 표출
        cachingResponseWrapper.copyBodyToResponse();
    }
}
