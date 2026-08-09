package soma.ghostrunner.domain.running.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
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
import soma.ghostrunner.domain.course.domain.RankSlot;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.PushService;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 러닝 쓰기 유즈케이스 ↔ 코스 리드모델 동기화 통합 테스트
 *
 * 설계 문서: docs/refactoring/course-read-model/core/04-detailed-design.md §0-3(쓰기 플로우), §3-2(유즈케이스)
 *
 * 검증 대상은 "러닝 쓰기 트랜잭션이 커밋될 때 리드모델이 정확히 한 번, 정확한 값으로 반영되는가" 다.
 * <ul>
 *   <li>러닝 종료 → {@code writer.applyRun} 직접 호출 (증분). 이벤트 리스너 경로와 <b>이중 반영되면 안 된다</b>.</li>
 *   <li>러닝 삭제 → {@code writer.recalculate} 직접 호출 (즉시 정정).</li>
 * </ul>
 *
 * 두 테스트 모두 <b>실제 커밋</b>을 거친다. 리드모델 갱신이 BEFORE_COMMIT 리스너에 있는 한
 * 롤백되는 테스트 트랜잭션 안에서는 아무 일도 일어나지 않아 검증 자체가 성립하지 않기 때문이다.
 */
@DisplayName("러닝 쓰기 → 코스 리드모델 동기화 통합 테스트")
@ExtendWith(DatabaseCleanserExtension.class)
class RunningReadModelSyncIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RunningCommandService runningCommandService;

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 러닝 종료 흐름의 외부 의존성만 모킹한다 (S3 업로드 / 시계열 가공 / 푸시 발송)
    @MockitoBean
    private RunningFileUploader runningFileUploader;

    @MockitoBean
    private TelemetryProcessor telemetryProcessor;

    @MockitoBean
    private PushService pushService;

    private TransactionTemplate requiresNewTemplate;

    private static final long STARTED_AT = 1750729987181L;

    @BeforeEach
    void setUp() {
        requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        when(telemetryProcessor.process(any(), any())).thenReturn(telemetryStatistics());
        when(runningFileUploader.uploadRawTelemetry(any(), anyString())).thenReturn("https://example.com/raw.jsonl");
        when(runningFileUploader.uploadInterpolatedTelemetry(anyList(), anyString())).thenReturn("https://example.com/interpolated.jsonl");
        when(runningFileUploader.uploadRunningCaptureImage(any(), anyString())).thenReturn("https://example.com/capture.png");
    }

    // ========== 1. 러닝 종료 → 증분 반영 (이중 반영 금지) ==========

    @DisplayName("공개 코스에서 러닝을 마치면 리드모델 TOP1에 반영되고 러너 수는 정확히 1이 된다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 러닝 저장 트랜잭션을 실제로 커밋시킨다
    void createRun_reflectsRecordToReadModelExactlyOnce() {
        // given : 공개 코스 + 빈 리드모델. 다른 러너의 일시정지 러닝이 하나 섞여 있다 (집계 제외 대상 — Q1)
        RunFixture fixture = requiresNewTemplate.execute(status -> {
            Member runner = saveMember("러너");
            Member pausedRunner = saveMember("일시정지러너");
            Course course = savePublicCourse("한강 코스", runner);
            savePublicReadModel(course);
            savePausedRunning(pausedRunner, course, 900L);
            return new RunFixture(course.getId(), runner.getId(), runner.getUuid());
        });

        // when : 공개 + 일시정지 아님 러닝을 종료한다
        Long runningId = requiresNewTemplate.execute(status ->
                runningCommandService.createRun(
                        createRunCommand(1800L, true, false),
                        fixture.runnerUuid(), fixture.courseId(),
                        file("raw.jsonl"), file("interpolated.jsonl"), file("capture.png")));

        // then : TOP1에 이번 기록이 반영된다
        assertThat(runningId).isNotNull();
        CourseReadModel readModel = readCommitted(fixture.courseId());
        assertThat(readModel.getTop1())
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(fixture.runnerId(), 1800);
        assertThat(readModel.getTop2()).isNull();

        // then : 러너 수는 방금 달린 한 명뿐이다.
        // 리드모델을 갱신하는 경로가 둘(리스너 + 직접 호출)이면 서로 다른 모집단으로 계산해 값이 어긋난다.
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);
    }

    // ========== 2. 러닝 삭제 → 즉시 정정 ==========

    @DisplayName("TOP1 러닝을 삭제하면 TOP1이 차순위로 교체되고 러너 수가 보정된다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deleteRunnings_recalculatesReadModel() {
        // given : 1등(1000초) · 2등(2000초)이 반영된 리드모델
        DeleteFixture fixture = requiresNewTemplate.execute(status -> {
            Member first = saveMember("1등");
            Member second = saveMember("2등");
            Course course = savePublicCourse("한강 코스", first);
            Running firstRunning = savePublicRunning(first, course, 1000L);
            savePublicRunning(second, course, 2000L);

            CourseReadModel readModel = savePublicReadModel(course);
            readModel.applyRun(first.getId(), 1000);
            readModel.applyRun(second.getId(), 2000);
            readModel.updateRunnersCount(2L);
            em.flush();

            return new DeleteFixture(course.getId(), first.getUuid(), firstRunning.getId(), second.getId());
        });

        // when : 1등 러닝을 삭제한다
        requiresNewTemplate.executeWithoutResult(status ->
                runningCommandService.deleteRunnings(List.of(fixture.topRunningId()), fixture.topRunnerUuid()));

        // then : TOP1은 차순위로 교체되고 러너 수도 보정된다
        CourseReadModel readModel = readCommitted(fixture.courseId());
        assertThat(readModel.getTop1())
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(fixture.secondRunnerId(), 2000);
        assertThat(readModel.getTop2()).isNull();
        assertThat(readModel.getRunnersCount()).isEqualTo(1L);
    }

    // ========== Fixtures & Helpers ==========

    private record RunFixture(Long courseId, Long runnerId, String runnerUuid) {
    }

    private record DeleteFixture(Long courseId, String topRunnerUuid, Long topRunningId, Long secondRunnerId) {
    }

    private CourseReadModel readCommitted(Long courseId) {
        return requiresNewTemplate.execute(status ->
                readModelRepository.findByCourseId(courseId).orElseThrow());
    }

    private CreateRunCommand createRunCommand(Long durationSeconds, boolean isPublic, boolean hasPaused) {
        RunRecordCommand record = new RunRecordCommand(
                5.2, 40.0, -20.0, durationSeconds, 6.1, 302, 120, 56);
        return new CreateRunCommand(
                "테스트 러닝", null, RunningMode.SOLO.name(), STARTED_AT, record, hasPaused, isPublic);
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
        Course course = Course.of(
                owner,
                name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(37.5, 127.0),
                CourseSource.USER,
                true,
                CourseDataUrls.of(
                        "https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumbnail.jpg")
        );
        return courseRepository.save(course);
    }

    private CourseReadModel savePublicReadModel(Course course) {
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        return readModelRepository.save(readModel);
    }

    private Running savePublicRunning(Member member, Course course, Long durationSeconds) {
        return saveRunning(member, course, durationSeconds, false);
    }

    private Running savePausedRunning(Member member, Course course, Long durationSeconds) {
        return saveRunning(member, course, durationSeconds, true);
    }

    private Running saveRunning(Member member, Course course, Long durationSeconds, boolean hasPaused) {
        RunningRecord record = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, durationSeconds, 302, 120, 56);
        Running running = Running.of("테스트 러닝", RunningMode.SOLO, null, record, STARTED_AT,
                true, hasPaused, "URL", "URL", "URL", member, course);
        return runningRepository.save(running);
    }
}
