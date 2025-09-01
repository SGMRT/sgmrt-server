package soma.ghostrunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import soma.ghostrunner.clients.aws.presign.S3PresignUrlClient;
import soma.ghostrunner.domain.auth.api.AuthApi;
import soma.ghostrunner.domain.auth.application.AuthService;
import soma.ghostrunner.domain.running.api.RunningApi;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapperImpl;
import soma.ghostrunner.domain.running.application.PaceMakerService;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.application.RunningTelemetryQueryService;
import soma.ghostrunner.global.common.CommonApi;
import soma.ghostrunner.global.common.log.HttpLogger;
import soma.ghostrunner.global.security.jwt.support.JwtProvider;

@WebMvcTest(controllers = {RunningApi.class, AuthApi.class, CommonApi.class})
@Import(RunningApiMapperImpl.class)
@WithMockUser
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
    protected AuthService authService;

    @MockitoBean
    protected PaceMakerService paceMakerService;

    @MockitoBean
    protected HttpLogger httpLogger;

    @MockitoBean
    protected JwtProvider jwtProvider;

    @MockitoBean
    protected S3PresignUrlClient s3PresignUrlClient;

}
