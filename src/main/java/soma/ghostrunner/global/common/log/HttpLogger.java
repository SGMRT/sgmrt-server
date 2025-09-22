package soma.ghostrunner.global.common.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.Set;

@Slf4j
@Component
public class HttpLogger {

    private static final Set<String> LOG_EXCLUSION_URIS = Set.of(
            "/",
            "/favicon.ico",
            "/actuator/prometheus",
            "/actuator/health"
    );

    public void log(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, double elapsedTime) {
        if (LOG_EXCLUSION_URIS.contains(request.getRequestURI())) {
            return;
        }
        HttpLogMessage logMessage = HttpLogMessage.of(request, response, elapsedTime);
        log.info(logMessage.toPrettierLog());
    }

}
