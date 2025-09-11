package soma.ghostrunner.global.common.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
public class HttpLogger {

    public void log(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, double elapsedTime) {
        HttpLogMessage logMessage = HttpLogMessage.of(request, response, elapsedTime);
//        log.info(logMessage.toPrettierLog());
    }

}
