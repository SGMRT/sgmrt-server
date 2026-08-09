package soma.ghostrunner.domain.running.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.application.MemberVdotWriter;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.PushService;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 러닝 생성의 <b>트랜잭션 경계</b> 통합 테스트.
 *
 * <p>이 클래스의 테스트는 바깥 트랜잭션 없이({@code NOT_SUPPORTED}) 유즈케이스를 그대로 호출한다.
 * 프로덕션에서 컨트롤러에 트랜잭션이 없다는 사실을 그대로 재현하는 것이고, 그래서 두 가지가 한꺼번에 증명된다.
 * <ul>
 *   <li><b>저장 경계는 {@link RunningWriter}가 스스로 연다</b> — 거기서 {@code @Transactional}을 떼면
 *       MANDATORY 인 {@code CourseReadModelWriter.applyRun}이 즉시 터져 이 테스트가 빨개진다.</li>
 *   <li><b>VDOT는 그 경계 밖이다</b> — VDOT가 실패해도 러닝은 이미 커밋돼 있어야 한다. VDOT 호출을 저장
 *       트랜잭션 안으로 되돌리면 롤백되어 러닝이 사라지고, 아래 단언이 빨개진다.</li>
 * </ul>
 */
@DisplayName("러닝 생성 트랜잭션 경계 통합 테스트")
@ExtendWith(DatabaseCleanserExtension.class)
class RunningCreationTransactionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RunningCommandService runningCommandService;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 러닝 종료 흐름의 외부 의존성만 모킹한다 (S3 업로드 / 시계열 가공 / 푸시 발송)
    @MockitoBean
    private RunningFileUploader runningFileUploader;

    @MockitoBean
    private TelemetryProcessor telemetryProcessor;

    @MockitoBean
    private PushService pushService;

    /** VDOT 실패를 주입하기 위한 모킹 — 이 테스트의 대상은 "실패했을 때 무슨 일이 벌어지는가" 다. */
    @MockitoBean
    private MemberVdotWriter memberVdotWriter;

    private static final long STARTED_AT = 1750729987181L;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        when(telemetryProcessor.process(any(), any())).thenReturn(telemetryStatistics());
        when(runningFileUploader.uploadRawTelemetry(any(), anyString())).thenReturn("https://example.com/raw.jsonl");
        when(runningFileUploader.uploadInterpolatedTelemetry(anyList(), anyString())).thenReturn("https://example.com/interpolated.jsonl");
        when(runningFileUploader.uploadRunningCaptureImage(any(), anyString())).thenReturn("https://example.com/capture.png");

        serviceLogger = (Logger) LoggerFactory.getLogger(RunningCommandService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @DisplayName("VDOT 갱신이 실패해도 러닝은 커밋되고, 예외는 호출자에게 전파되지 않는다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 저장 트랜잭션을 실제로 커밋시킨다
    void createRun_vdotFails_runStillCommittedAndAlerted() {
        // given : 공개 코스 + 리드모델. VDOT 갱신만 실패한다
        Fixture fixture = newTransaction().execute(status -> {
            Member runner = saveMember("러너");
            Course course = savePublicCourse("한강 코스", runner);
            savePublicReadModel(course);
            return new Fixture(course.getId(), runner.getUuid());
        });
        doThrow(new IllegalStateException("vdot down"))
                .when(memberVdotWriter).updateFromRun(anyString(), any());

        // when : 컨트롤러가 부르듯 바깥 트랜잭션 없이 호출한다
        Long runningId = runningCommandService.createRun(
                createRunCommand(1800L), fixture.runnerUuid(), fixture.courseId(),
                file("raw.jsonl"), file("interpolated.jsonl"), file("capture.png"));

        // then : 러닝은 커밋됐다 (다른 트랜잭션에서 읽힌다)
        assertThat(runningId).isNotNull();
        Optional<Running> committedRunning =
                newTransaction().execute(status -> runningRepository.findById(runningId));
        assertThat(committedRunning).isPresent();

        // then : 리드모델도 같은 트랜잭션에서 함께 커밋됐다 (부분 저장이 아니다)
        Long runnersCount = newTransaction().execute(status ->
                readModelRepository.findByCourseId(fixture.courseId()).orElseThrow().getRunnersCount());
        assertThat(runnersCount).isEqualTo(1L);

        // then : 실패는 조용히 사라지지 않는다 — prod 에서 ERROR 로그는 SentryAppender 로 알럿이 된다
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("VDOT 갱신 실패");
                });
    }

    // ========== Fixtures & Helpers ==========

    private record Fixture(Long courseId, String runnerUuid) {
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private CreateRunCommand createRunCommand(Long durationSeconds) {
        RunRecordCommand record = new RunRecordCommand(
                5.2, 40.0, -20.0, durationSeconds, 6.1, 302, 120, 56);
        return new CreateRunCommand(
                "테스트 러닝", null, RunningMode.SOLO.name(), STARTED_AT, record, false, true);
    }

    private TelemetryStatistics telemetryStatistics() {
        return new TelemetryStatistics(
                List.of(), new Coordinates(37.5, 127.0), 6.9, 4.9, 30.0, 5.2);
    }

    private MultipartFile file(String name) {
        return new MockMultipartFile(name, name, "application/octet-stream",
                "{}".getBytes(StandardCharsets.UTF_8));
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "https://example.com/" + nickname + ".jpg"));
    }

    private Course savePublicCourse(String name, Member owner) {
        return courseRepository.save(Course.of(
                owner,
                name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(37.5, 127.0),
                CourseSource.USER,
                true,
                CourseDataUrls.of(
                        "https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumbnail.jpg")));
    }

    private void savePublicReadModel(Course course) {
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        readModelRepository.save(readModel);
    }
}
