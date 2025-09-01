package soma.ghostrunner.domain.running.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.infra.RunningRepository;

class RunningTelemetryQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningTelemetryQueryService runningTelemetryQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    RunningRepository runningRepository;

    @MockitoBean
    GhostRunnerS3Client ghostRunnerS3Client;

}
