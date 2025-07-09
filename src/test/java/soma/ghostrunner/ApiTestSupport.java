package soma.ghostrunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import soma.ghostrunner.domain.running.api.RunningApi;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapperImpl;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.application.RunningTelemetryQueryService;
import soma.ghostrunner.global.common.log.HttpLogger;

@WebMvcTest(controllers = RunningApi.class)
@Import(RunningApiMapperImpl.class)
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    RunningApiMapper runningApiMapper;

    @MockitoBean
    protected RunningCommandService runningCommandService;

    @MockitoBean
    protected RunningQueryService runningQueryService;

    @MockitoBean
    protected RunningTelemetryQueryService runningTelemetryQueryService;

    @MockitoBean
    protected HttpLogger httpLogger;

}
