package soma.ghostrunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import soma.ghostrunner.domain.auth.api.AuthApi;
import soma.ghostrunner.domain.auth.application.AuthService;
import soma.ghostrunner.domain.device.api.DeviceApi;
import soma.ghostrunner.domain.device.application.DeviceService;
import soma.ghostrunner.domain.member.api.MemberApi;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.notice.api.NoticeApi;
import soma.ghostrunner.domain.notice.application.NoticeService;
import soma.ghostrunner.domain.notification.api.NotificationApi;
import soma.ghostrunner.domain.notification.application.PushService;
import soma.ghostrunner.domain.running.api.PacemakerApi;
import soma.ghostrunner.domain.running.api.RunningApi;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.api.support.RunningApiMapperImpl;
import soma.ghostrunner.domain.running.application.PacemakerService;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.global.clients.aws.s3.GhostRunnerS3PresignUrlClient;
import soma.ghostrunner.global.common.log.HttpLogger;
import soma.ghostrunner.global.security.jwt.support.JwtProvider;

@WebMvcTest(controllers = {RunningApi.class, AuthApi.class, NoticeApi.class, MemberApi.class, PacemakerApi.class, DeviceApi.class, NotificationApi.class})
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
    protected AuthService authService;

    @MockitoBean
    protected PacemakerService paceMakerService;

    @MockitoBean
    protected NoticeService noticeService;

    @MockitoBean
    protected MemberService memberService;

    @MockitoBean
    protected DeviceService deviceService;

    @MockitoBean
    protected HttpLogger httpLogger;

    @MockitoBean
    protected JwtProvider jwtProvider;

    @MockitoBean
    protected GhostRunnerS3PresignUrlClient ghostRunnerS3PresignUrlClient;

    @MockitoBean
    protected PushService pushService;

}
