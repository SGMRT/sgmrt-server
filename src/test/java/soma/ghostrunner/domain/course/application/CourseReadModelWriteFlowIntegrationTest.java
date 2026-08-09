package soma.ghostrunner.domain.course.application;

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
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.RankSlot;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.application.PushService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.RunningCommandService;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static soma.ghostrunner.domain.course.dto.request.CoursePatchRequest.UpdatedAttr.IS_PUBLIC;
import static soma.ghostrunner.domain.course.dto.request.CoursePatchRequest.UpdatedAttr.NAME;

/**
 * 리드모델 쓰기 경로 전체를 관통하는 E2E 시나리오 테스트.
 *
 * 설계 문서: docs/refactoring/course-read-model/core/04-detailed-design.md §3-2(유즈케이스별 흐름)
 *
 * <p>개별 유즈케이스(러닝 종료 증분 · 러닝 삭제 재계산 · 코스 등록 초기화)는 각각
 * {@code RunningReadModelSyncIntegrationTest}, {@code CourseWriterTest}가 이미 덮는다.
 * 여기서 검증하는 것은 <b>여러 유즈케이스가 이어졌을 때도 리드모델이 원본과 계속 일치하는가</b> 다.
 * 증분(applyRun)과 재계산(recalculate)이 번갈아 실행되며 서로의 결과를 덮어쓰기 때문에,
 * 각 단계가 개별적으로 옳아도 이어 붙이면 어긋날 수 있다.</p>
 *
 * <p>두 시나리오 모두 서비스 레이어({@link RunningCommandService}, {@link CourseWriter})를 통해
 * 실제 유즈케이스를 태우고, 각 단계를 <b>실제로 커밋</b>한 뒤 다음 트랜잭션에서 읽어 검증한다.</p>
 */
@DisplayName("코스 리드모델 쓰기 경로 E2E 시나리오 테스트")
@ExtendWith(DatabaseCleanserExtension.class)
class CourseReadModelWriteFlowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RunningCommandService runningCommandService;

    @Autowired
    private CourseWriter courseWriter;

    @Autowired
    private CourseReadModelRepository readModelRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 러닝 흐름의 외부 의존성만 모킹한다 (S3 업로드 / 시계열 가공 / 푸시 발송)
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
        when(runningFileUploader.uploadSimplifiedCoordinates(anyList(), anyString())).thenReturn("https://example.com/simplified.json");
        when(runningFileUploader.uploadCheckpoints(anyList(), anyString())).thenReturn("https://example.com/checkpoints.json");
        when(runningFileUploader.uploadRunningCaptureImage(any(), anyString())).thenReturn("https://example.com/capture.png");
    }

    // ========== 시나리오 1. 코스 수명주기 ==========

    @DisplayName("코스가 생성-공개-달림-변경-비공개-삭제되는 동안 리드모델이 코스와 러닝을 계속 따라간다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 각 단계를 실제로 커밋시킨다
    void courseLifecycle_readModelFollowsEveryTransition() {
        // given : 러너 세 명
        Runner owner = saveRunner("주인");
        Runner fastRunner = saveRunner("빠른러너");
        Runner slowRunner = saveRunner("느린러너");

        // when 1 : 일반 러닝 → 러닝 + 코스가 함께 생성된다 (코스는 비공개로 태어난다)
        CreateCourseAndRunResponse created = createRunAndCourse(owner, 1800L);
        Long courseId = created.getCourseId();

        // then 1 : 비공개 코스는 지도에 뜨지 않으므로 리드모델 자체가 없다
        assertThat(findReadModel(courseId)).isEmpty();

        // when 2 : 코스를 공개로 전환한다 (이름도 함께 지정)
        updateCourse(courseId, patch("한강 코스", true, NAME, IS_PUBLIC), owner.uuid());

        // then 2 : 리드모델이 생성되고 이미 쌓여있던 러닝으로 초기화된다
        CourseReadModel afterPublish = readModel(courseId);
        assertThat(afterPublish.getIsPublic()).isTrue();
        assertThat(afterPublish.getName()).isEqualTo("한강 코스");
        assertThat(afterPublish.getRunnersCount()).isEqualTo(1L);
        assertSlot(afterPublish.getTop1(), owner, 1800);
        assertThat(afterPublish.getTop2()).isNull();

        // when 3 : 다른 러너 두 명이 이 코스를 달린다
        createRun(fastRunner, courseId, 1500L);
        createRun(slowRunner, courseId, 2000L);

        // then 3 : 기록 순으로 TOP에 채워지고 러너 수는 세 명이 된다
        CourseReadModel afterOtherRunners = readModel(courseId);
        assertSlot(afterOtherRunners.getTop1(), fastRunner, 1500);
        assertSlot(afterOtherRunners.getTop2(), owner, 1800);
        assertSlot(afterOtherRunners.getTop3(), slowRunner, 2000);
        assertThat(afterOtherRunners.getTop4()).isNull();
        assertThat(afterOtherRunners.getRunnersCount()).isEqualTo(3L);

        // when 4 : 이미 달렸던 러너가 자기 기록을 경신한다
        createRun(slowRunner, courseId, 1200L);

        // then 4 : 같은 러너가 두 슬롯을 차지하지 않고 순위만 재정렬되며, 러너 수도 늘지 않는다
        CourseReadModel afterPersonalBest = readModel(courseId);
        assertSlot(afterPersonalBest.getTop1(), slowRunner, 1200);
        assertSlot(afterPersonalBest.getTop2(), fastRunner, 1500);
        assertSlot(afterPersonalBest.getTop3(), owner, 1800);
        assertThat(afterPersonalBest.getTop4()).isNull();
        assertThat(afterPersonalBest.getRunnersCount()).isEqualTo(3L);

        // when 5 : 코스명을 바꾼다
        updateCourse(courseId, patch("여의도 코스", null, NAME), owner.uuid());

        // then 5 : 역정규화된 이름이 동기화되고 순위는 그대로다
        CourseReadModel afterRename = readModel(courseId);
        assertThat(afterRename.getName()).isEqualTo("여의도 코스");
        assertSlot(afterRename.getTop1(), slowRunner, 1200);
        assertThat(afterRename.getRunnersCount()).isEqualTo(3L);

        // when 6 : 코스를 비공개로 되돌린다 (등록 해제)
        updateCourse(courseId, patch(null, false, IS_PUBLIC), owner.uuid());

        // then 6 : 지도에서 빠지되(isPublic=false) 집계는 남는다 — 재등록 시 그대로 쓰인다
        CourseReadModel afterUnpublish = readModel(courseId);
        assertThat(afterUnpublish.getIsPublic()).isFalse();
        assertSlot(afterUnpublish.getTop1(), slowRunner, 1200);
        assertThat(afterUnpublish.getRunnersCount()).isEqualTo(3L);

        // when 7 : 코스를 삭제한다
        deleteCourse(courseId, owner.uuid());

        // then 7 : 리드모델도 함께 사라진다
        assertThat(findReadModel(courseId)).isEmpty();
    }

    // ========== 시나리오 2. 정정 ==========

    @DisplayName("집계 대상 러닝이 사라지면 삭제든 비공개 전환이든 리드모델이 즉시 정정된다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void runningRemoval_correctsReadModelImmediately() {
        // given : 러너 두 명이 달린 공개 코스 (1등 1000초 · 2등 2000초)
        Runner firstRunner = saveRunner("1등");
        Runner secondRunner = saveRunner("2등");

        CreateCourseAndRunResponse created = createRunAndCourse(firstRunner, 1000L);
        Long courseId = created.getCourseId();
        Long firstRunningId = created.getRunningId();

        updateCourse(courseId, patch("한강 코스", true, NAME, IS_PUBLIC), firstRunner.uuid());
        Long secondRunningId = createRun(secondRunner, courseId, 2000L);

        CourseReadModel before = readModel(courseId);
        assertSlot(before.getTop1(), firstRunner, 1000);
        assertSlot(before.getTop2(), secondRunner, 2000);
        assertThat(before.getRunnersCount()).isEqualTo(2L);

        // when 1 : TOP1 러너가 자기 러닝을 삭제한다
        deleteRunnings(List.of(firstRunningId), firstRunner.uuid());

        // then 1 : TOP1이 차순위로 즉시 교체되고 러너 수도 보정된다
        CourseReadModel afterDelete = readModel(courseId);
        assertSlot(afterDelete.getTop1(), secondRunner, 2000);
        assertThat(afterDelete.getTop2()).isNull();
        assertThat(afterDelete.getRunnersCount()).isEqualTo(1L);

        // when 2 : 남은 러너가 자기 러닝을 비공개로 돌린다
        updateRunningPublicStatus(secondRunningId, secondRunner.uuid());

        // then 2 : 집계 대상이 하나도 남지 않으므로 TOP4는 전부 비고 러너 수는 0이 된다
        CourseReadModel afterUnpublishRun = readModel(courseId);
        assertThat(afterUnpublishRun.getTop1()).isNull();
        assertThat(afterUnpublishRun.getTop2()).isNull();
        assertThat(afterUnpublishRun.getTop3()).isNull();
        assertThat(afterUnpublishRun.getTop4()).isNull();
        assertThat(afterUnpublishRun.getRunnersCount()).isZero();
    }

    // ========== 유즈케이스 호출 (각 호출이 하나의 커밋) ==========

    private CreateCourseAndRunResponse createRunAndCourse(Runner runner, Long durationSeconds) {
        return requiresNewTemplate.execute(status ->
                runningCommandService.createRunAndCourse(
                        createRunCommand(durationSeconds),
                        runner.uuid(),
                        file("raw.jsonl"), file("interpolated.jsonl"), file("capture.png")));
    }

    private Long createRun(Runner runner, Long courseId, Long durationSeconds) {
        return requiresNewTemplate.execute(status ->
                runningCommandService.createRun(
                        createRunCommand(durationSeconds),
                        runner.uuid(), courseId,
                        file("raw.jsonl"), file("interpolated.jsonl"), file("capture.png")));
    }

    private void updateCourse(Long courseId, CoursePatchRequest request, String memberUuid) {
        requiresNewTemplate.executeWithoutResult(status ->
                courseWriter.updateCourse(courseId, request, memberUuid));
    }

    private void deleteCourse(Long courseId, String memberUuid) {
        requiresNewTemplate.executeWithoutResult(status ->
                courseWriter.deleteCourse(courseId, memberUuid));
    }

    private void deleteRunnings(List<Long> runningIds, String memberUuid) {
        requiresNewTemplate.executeWithoutResult(status ->
                runningCommandService.deleteRunnings(runningIds, memberUuid));
    }

    private void updateRunningPublicStatus(Long runningId, String memberUuid) {
        requiresNewTemplate.executeWithoutResult(status ->
                runningCommandService.updateRunningPublicStatus(runningId, memberUuid));
    }

    // ========== 검증 헬퍼 ==========

    private CourseReadModel readModel(Long courseId) {
        return findReadModel(courseId)
                .orElseThrow(() -> new AssertionError("리드모델이 존재하지 않습니다. courseId=" + courseId));
    }

    private Optional<CourseReadModel> findReadModel(Long courseId) {
        return requiresNewTemplate.execute(status -> readModelRepository.findByCourseId(courseId));
    }

    private void assertSlot(RankSlot slot, Runner runner, int timeSeconds) {
        assertThat(slot)
                .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                .containsExactly(runner.id(), timeSeconds);
    }

    // ========== 픽스처 ==========

    private record Runner(Long id, String uuid) {
    }

    private Runner saveRunner(String nickname) {
        return requiresNewTemplate.execute(status -> {
            Member member = memberRepository.save(Member.of(nickname, "https://example.com/" + nickname + ".jpg"));
            return new Runner(member.getId(), member.getUuid());
        });
    }

    private CoursePatchRequest patch(String name, Boolean isPublic, CoursePatchRequest.UpdatedAttr... attrs) {
        return new CoursePatchRequest(name, isPublic, Set.of(attrs));
    }

    /** 집계 대상이 되는 러닝 (공개 + 일시정지 없음) */
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
}
