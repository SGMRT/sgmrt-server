package soma.ghostrunner.domain.running.application;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseMapCacheEvictor;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.application.MemberVdotWriter;
import soma.ghostrunner.domain.course.application.CourseSubscriptionService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;
import soma.ghostrunner.domain.running.domain.path.*;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class RunningCommandServiceTest {

    @Mock RunningApplicationMapper mapper;
    @Mock RunningRepository runningRepository;
    @Mock TelemetryProcessor telemetryProcessor;
    @Mock RunningFileUploader runningFileUploader;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock PathSimplificationService pathSimplificationService;
    @Mock RunningQueryService runningQueryService;
    @Mock CourseService courseService;
    @Mock MemberService memberService;
    @Mock CourseReadModelWriter courseReadModelWriter;
    @Mock MemberVdotWriter memberVdotWriter;
    @Mock CourseSubscriptionService courseSubscriptionService;
    @Mock CourseMapCacheEvictor courseMapCacheEvictor;

    RunningCommandService sut;

    // 고정 픽스처
    private final String memberUuid = "mem-123";
    private final long startedAt = LocalDateTime.of(2025, 8, 1, 7, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli();

    @BeforeEach
    void setUp() {
        sut = new RunningCommandService(
                mapper, runningRepository,
                telemetryProcessor, runningFileUploader, applicationEventPublisher,
                pathSimplificationService, runningQueryService, courseService, memberService,
                courseReadModelWriter, memberVdotWriter, courseSubscriptionService,
                courseMapCacheEvictor
        );
    }

    private MultipartFile raw() {
        return new MockMultipartFile(
                "raw.jsonl", "raw.jsonl", "application/json",
                "[{\"t\":0}]".getBytes(StandardCharsets.UTF_8)
        );
    }

    private MultipartFile interp() {
        return new MockMultipartFile(
                "interp.jsonl", "interp.jsonl", "application/json",
                "[{\"t\":0,\"x\":127.0,\"y\":37.0}]".getBytes(StandardCharsets.UTF_8)
        );
    }

    private MultipartFile shot() {
        return new MockMultipartFile(
                "cap.png", "cap.png", "image/png", new byte[]{1, 2, 3}
        );
    }

    private TelemetryStatistics statsMock() {
        return mock(TelemetryStatistics.class, RETURNS_DEEP_STUBS);
    }

    /** createRun 진입점에서 조회되는 코스 */
    private Course givenFoundCourse(long courseId) {
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);
        when(courseService.findCourseByIdFetchJoinMember(courseId)).thenReturn(course);
        return course;
    }

    /** createRun 진입점에서 조회되는 러너 */
    private Member givenFoundRunner(long memberId) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        when(member.getUuid()).thenReturn(memberUuid);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);
        return member;
    }

    /** 시계열 가공 결과 */
    private TelemetryStatistics givenProcessedTelemetry() {
        TelemetryStatistics stats = statsMock();
        when(telemetryProcessor.process(any(MultipartFile.class), eq(startedAt))).thenReturn(stats);
        return stats;
    }

    // ====== createRunAndCourse ======
    @Test
    @DisplayName("createRunAndCourse: 원시/보간/간소화 업로드 → 코스/러닝 저장 → 응답 매핑까지 오케스트레이션")
    void createRunAndCourse_success_orchestration() {
        // given
        Member member = mock(Member.class);
        when(member.getUuid()).thenReturn(memberUuid); // 서비스는 member.getUuid()를 사용함
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        TelemetryStatistics stats = statsMock();
        when(telemetryProcessor.process(any(MultipartFile.class), eq(startedAt))).thenReturn(stats);

        SimplifiedPaths simplified = new SimplifiedPaths(
                List.of(new Coordinates(37.0, 127.0), new Coordinates(37.001, 127.001)),
                List.of(new Checkpoint(37.0, 127.0, 0))
        );
        when(pathSimplificationService.simplify(stats)).thenReturn(simplified);

        // 업로드 결과 URL
        when(runningFileUploader.uploadRawTelemetry(any(), eq(memberUuid))).thenReturn("s3://raw");
        when(runningFileUploader.uploadInterpolatedTelemetry(anyList(), eq(memberUuid))).thenReturn("s3://interp");
        when(runningFileUploader.uploadSimplifiedCoordinates(anyList(), eq(memberUuid))).thenReturn("s3://simplified");
        when(runningFileUploader.uploadCheckpoints(anyList(), eq(memberUuid))).thenReturn("s3://checkpoints");
        when(runningFileUploader.uploadRunningCaptureImage(any(), eq(memberUuid))).thenReturn("s3://shot");

        // 매핑 및 저장
        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);

        Course course = mock(Course.class);
        when(course.getId()).thenReturn(500L);
        when(mapper.toCourse(eq(member), eq(cmd), eq(stats), any(RunningDataUrlsDto.class))).thenReturn(course);

        Running running = mock(Running.class);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(running)).thenReturn(running);
        when(running.getId()).thenReturn(1L);
        when(running.getRunningRecord()).thenReturn(
                RunningRecord.of(5.2, 30.0, 40.0, -20.0, 6.1, 4.9, 6.9, 1800L, 302, 120, 56));

        CreateCourseAndRunResponse response = new CreateCourseAndRunResponse(null, null);
        when(mapper.toResponse(running, course)).thenReturn(response);

        // when
        CreateCourseAndRunResponse result =
                sut.createRunAndCourse(cmd, memberUuid, raw(), interp(), shot());

        // then
        assertThat(result).isSameAs(response);

        InOrder inOrder = inOrder(memberService, telemetryProcessor, pathSimplificationService,
                runningFileUploader, courseService, runningRepository, mapper);

        inOrder.verify(memberService).findMemberByUuid(memberUuid);
        inOrder.verify(telemetryProcessor).process(any(MultipartFile.class), eq(startedAt));
        inOrder.verify(pathSimplificationService).simplify(stats);

        verify(runningFileUploader).uploadRawTelemetry(any(), eq(memberUuid));
        verify(runningFileUploader).uploadInterpolatedTelemetry(anyList(), eq(memberUuid));
        verify(runningFileUploader).uploadSimplifiedCoordinates(anyList(), eq(memberUuid));
        verify(runningFileUploader).uploadCheckpoints(anyList(), eq(memberUuid));
        verify(runningFileUploader).uploadRunningCaptureImage(any(), eq(memberUuid));

        inOrder.verify(mapper).toCourse(eq(member), eq(cmd), eq(stats), any(RunningDataUrlsDto.class));
        inOrder.verify(courseService).save(course);
        inOrder.verify(mapper).toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course));
        inOrder.verify(runningRepository).save(running);
        // 이벤트 발행은 스프링 테스트하지 않음 (생략)
        inOrder.verify(mapper).toResponse(running, course);

        // 새 코스도 지도에 오르므로 그 셀의 캐시를 커밋 후 지운다
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(500L);

        verifyNoMoreInteractions(memberService, telemetryProcessor, pathSimplificationService,
                runningFileUploader, courseService, runningRepository, mapper);
    }

    // ====== createRun (코스에 붙여 저장) ======

    @Test
    @DisplayName("createRun (NORMAL 모드): 코스 조회 → 보간처리 → 업로드(원시/보간/스크린샷) → 러닝 저장 → ID 반환")
    void createRun_normal_success() {
        // given
        long courseId = 77L;
        Course course = givenFoundCourse(courseId);
        Member member = givenFoundRunner(5L);
        TelemetryStatistics stats = givenProcessedTelemetry();

        when(runningFileUploader.uploadRawTelemetry(any(), eq(memberUuid))).thenReturn("s3://raw");
        when(runningFileUploader.uploadInterpolatedTelemetry(anyList(), eq(memberUuid))).thenReturn("s3://interp");
        when(runningFileUploader.uploadRunningCaptureImage(any(), eq(memberUuid))).thenReturn("s3://shot");

        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);
        when(cmd.getMode()).thenReturn("NORMAL");

        Running running = mock(Running.class);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(any())).thenReturn(running);
        when(running.getId()).thenReturn(100L);
        when(running.getRunningRecord()).thenReturn(
                RunningRecord.of(5.2, 30.0, 40.0, -20.0, 6.1, 4.9, 6.9, 1800L, 302, 120, 56));

        // when
        Long id = sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then
        assertThat(id).isEqualTo(100L);
        verify(runningQueryService, never()).findRunningByRunningId(anyLong());
    }

    @Test
    @DisplayName("createRun (GHOST 모드): 고스트 러닝이 같은 코스에 속하는지 검증한다")
    void createRun_ghostMode_validatesBelongsToCourse() {
        // given
        long courseId = 88L;
        Course course = givenFoundCourse(courseId);
        Member member = givenFoundRunner(5L);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);
        when(cmd.getMode()).thenReturn("GHOST");
        when(cmd.getGhostRunningId()).thenReturn(999L);

        Running ghost = mock(Running.class);
        when(runningQueryService.findRunningByRunningId(999L)).thenReturn(ghost);

        // 러닝 저장 흐름
        Running running = mock(Running.class);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(any())).thenReturn(running);
        when(running.getId()).thenReturn(200L);
        when(running.getRunningRecord()).thenReturn(
                RunningRecord.of(5.2, 30.0, 40.0, -20.0, 6.1, 4.9, 6.9, 1800L, 302, 120, 56));

        // when
        Long id = sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then
        assertThat(id).isEqualTo(200L);
        verify(ghost).validateBelongsToCourse(courseId);
        verify(runningQueryService).findRunningByRunningId(999L);
    }

    // ====== 업데이트 계열 ======

    @Test
    @DisplayName("updateRunningName: 본인 검증 후 이름을 변경하고, 코스의 지도 셀 이빅트를 예약한다")
    void updateRunningName_updatesAfterOwnershipCheck() {
        // given
        Long runningId = 10L;
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(40L);

        Running running = mock(Running.class);
        when(running.getCourse()).thenReturn(course);
        when(runningQueryService.findRunningByRunningId(runningId)).thenReturn(running);

        // when
        sut.updateRunningName("새 이름", runningId, memberUuid);

        // then
        InOrder inOrder = inOrder(runningQueryService, running);
        inOrder.verify(runningQueryService).findRunningByRunningId(runningId);
        inOrder.verify(running).verifyMember(memberUuid);
        inOrder.verify(running).updateName("새 이름");

        // 러닝 이름은 지도 카드에 노출되므로 셀 캐시도 커밋 후 지워야 한다
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(40L);
    }

    @Test
    @DisplayName("updateRunningPublicStatus: 본인 검증 후 공개 상태를 토글한다")
    void updateRunningPublicStatus_togglesAfterOwnershipCheck() {
        // given
        Long runningId = 11L;
        Running running = mock(Running.class);
        when(runningQueryService.findRunningByRunningId(runningId)).thenReturn(running);

        // when
        sut.updateRunningPublicStatus(runningId, memberUuid);

        // then
        InOrder inOrder = inOrder(runningQueryService, running);
        inOrder.verify(runningQueryService).findRunningByRunningId(runningId);
        inOrder.verify(running).verifyMember(memberUuid);
        inOrder.verify(running).updatePublicStatus();
    }

    // ====== 삭제 ======

    @Test
    @DisplayName("deleteRunnings: 각 러닝에 대해 소유자 검증 후 일괄 삭제 요청한다")
    void deleteRunnings_verifiesOwnershipThenDeletes() {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);
        Running r1 = mock(Running.class);
        Running r2 = mock(Running.class);
        Running r3 = mock(Running.class);

        when(runningRepository.findByIds(ids)).thenReturn(List.of(r1, r2, r3));

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        verify(r1).verifyMember(memberUuid);
        verify(r2).verifyMember(memberUuid);
        verify(r3).verifyMember(memberUuid);
        verify(runningRepository).deleteInRunningIds(ids);
    }

    // ====== 코스 리드모델 동기화 ======
    // 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md §0-3, §3-2
    // 러닝 쓰기 유즈케이스가 CourseReadModelWriter 를 직접 호출한다 (이벤트 경유 X).

    @Test
    @DisplayName("createRun: 공개 + 일시정지 아닌 러닝이면 저장된 러닝 정보로 리드모델을 증분 갱신한다")
    void createRun_publicRun_appliesRunToReadModel() {
        // given
        long courseId = 77L;
        long memberId = 5L;
        long runningId = 100L;
        long durationSeconds = 1800L;

        Course course = givenFoundCourse(courseId);
        Member member = givenFoundRunner(memberId);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = publicRunCommand(durationSeconds, false);
        Running running = savedPublicRunning(runningId, durationSeconds, member, course);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(any())).thenReturn(running);

        // when
        sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then : 저장된 러닝 객체를 그대로 위임한다 (집계 대상 판정·필드 추출은 Writer 책임)
        verify(courseReadModelWriter, times(1)).applyRun(running);
        // 같은 트랜잭션 동기 로직 — 이벤트가 아닌 직접 호출로 위임된다 (설계 04 §6)
        verify(memberVdotWriter, times(1)).updateFromRun(any(), any());
        verify(courseSubscriptionService, times(1)).subscribeIfAbsent(eq(courseId), any());
    }

    @Test
    @DisplayName("createRun: 일시정지 러닝도 Writer 로 위임된다 — 집계 대상 필터링은 Writer 책임")
    void createRun_pausedRun_delegatesToWriter() {
        // given
        long courseId = 77L;
        long memberId = 5L;
        long runningId = 101L;
        long durationSeconds = 1800L;

        Course course = givenFoundCourse(courseId);
        Member member = givenFoundRunner(memberId);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = publicRunCommand(durationSeconds, true);
        Running running = savedPausedRunning(runningId, durationSeconds, member, course);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(any())).thenReturn(running);

        // when
        sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then : 서비스는 필터링하지 않고 위임만 한다 (일시정지 제외는 CourseReadModelWriterTest 가 검증)
        verify(courseReadModelWriter, times(1)).applyRun(running);
    }

    /**
     * "완주 직후 지도에서 내 등수를 본다"(설계 §1-2) — 지도 셀 이빅트는 이제 이벤트가 아니라
     * {@code CourseMapCacheEvictor} 직접 호출이 담당한다. 커밋 후 타이밍 자체는 이빅터가 지킨다.
     *
     * 설계 문서: docs/design/course-cell-bucket-cache-design.md §1-2 · §4
     */
    @Test
    @DisplayName("createRun: 코스 따라 러닝 완주는 그 코스의 지도 셀 이빅트를 커밋 후로 예약한다")
    void createRun_schedulesMapCellEviction() {
        // given
        long courseId = 77L;
        Course course = givenFoundCourse(courseId);
        Member member = givenFoundRunner(5L);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = publicRunCommand(1800L, false);
        Running running = savedPublicRunning(100L, 1800L, member, course);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(any())).thenReturn(running);

        // when
        sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then : 커밋 후 리드모델이 남아 있으므로 좌표는 courseId로 역산한다
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(courseId);
    }

    /**
     * 이 발행의 남은 소비자는 구경로 코스 캐시 무효화(CourseCacheEventListener) 하나뿐이다(설계 결정 10).
     * 구경로가 사라지면 이 발행도 함께 사라지지만, <b>그 전에 먼저 지우면 구경로 무효화가 죽는다.</b>
     * 그 삭제 사고를 사람의 기억이 아니라 이 테스트가 잡는다.
     */
    @Test
    @DisplayName("createRun: 구경로 캐시 무효화가 소비하는 RunFinishedEvent 발행은 유지한다")
    void createRun_publishesRunFinishedEventForMapCacheEviction() {
        // given
        long courseId = 77L;
        long memberId = 5L;
        long runningId = 100L;
        long durationSeconds = 1800L;

        Course course = givenFoundCourse(courseId);
        Member member = givenFoundRunner(memberId);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = publicRunCommand(durationSeconds, false);
        Running running = savedPublicRunning(runningId, durationSeconds, member, course);
        RunFinishedEvent finishedEvent =
                new RunFinishedEvent(runningId, courseId, memberUuid, memberId, (int) durationSeconds, 6.1);
        when(running.createFinishedEvent()).thenReturn(finishedEvent);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(any())).thenReturn(running);

        // when
        sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then : 완주 이벤트가 그대로 발행된다
        verify(applicationEventPublisher).publishEvent(finishedEvent);
    }

    @Test
    @DisplayName("deleteRunnings: 삭제된 러닝들의 코스를 중복 없이 모아 한 번에 재계산한다")
    void deleteRunnings_recalculatesDistinctCourses() {
        // given : 같은 코스의 러닝 2건 + 다른 코스의 러닝 1건
        List<Long> ids = List.of(1L, 2L, 3L);

        Course courseA = mock(Course.class);
        when(courseA.getId()).thenReturn(10L);
        Course courseB = mock(Course.class);
        when(courseB.getId()).thenReturn(20L);

        givenRunningsOnCourses(ids, courseA, courseA, courseB);

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(courseReadModelWriter, times(1)).recalculate(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    /**
     * 셀 버킷 캐시는 좌표로만 이빅트하므로, 삭제로 TOP4가 바뀐 코스는 좌표와 함께 이빅트를 예약해야 한다(M1).
     * 좌표 수집은 반드시 벌크 삭제 이전이어야 한다 — deleteInRunningIds 가
     * {@code @Modifying(clearAutomatically = true)} 라 그 뒤에는 LAZY Course 프록시가
     * 미초기화 상태로 detach 되어 초기화할 수 없다([R2]). 좌표 추출을 삭제 뒤로 옮기면 이 테스트가 빨개진다.
     *
     * 설계 문서: docs/design/course-cell-bucket-cache-design.md §4(경로 e) · [R2] · D5
     */
    @Test
    @DisplayName("deleteRunnings: 영향받은 코스마다 시작점 좌표와 함께 지도 셀 이빅트를 1건씩 예약한다 [R2]")
    void deleteRunnings_schedulesMapCellEvictionPerDistinctCourse() {
        // given : 같은 코스의 러닝 2건 + 다른 코스의 러닝 1건 → 이빅트는 코스당 1건씩 총 2건
        List<Long> ids = List.of(1L, 2L, 3L);

        AtomicBoolean persistenceContextCleared = new AtomicBoolean(false);
        Course courseA = lazyCourse(10L, 37.5665, 126.9780, persistenceContextCleared);
        Course courseB = lazyCourse(20L, 35.1796, 129.0756, persistenceContextCleared);

        givenRunningsOnCourses(ids, courseA, courseA, courseB);

        // 벌크 삭제가 영속성 컨텍스트를 비운다 (clearAutomatically = true)
        doAnswer(invocation -> {
            persistenceContextCleared.set(true);
            return null;
        }).when(runningRepository).deleteInRunningIds(ids);

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        ArgumentCaptor<Long> courseIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Double> latCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lngCaptor = ArgumentCaptor.forClass(Double.class);
        verify(courseMapCacheEvictor, times(2))
                .evictCellAfterCommit(courseIdCaptor.capture(), latCaptor.capture(), lngCaptor.capture());

        assertThat(zip(courseIdCaptor.getAllValues(), latCaptor.getAllValues(), lngCaptor.getAllValues()))
                .containsExactlyInAnyOrder(
                        tuple(10L, 37.5665, 126.9780),
                        tuple(20L, 35.1796, 129.0756));
    }

    /** 캡터 세 개를 (courseId, lat, lng) 튜플로 맞춰 본다. */
    private List<org.assertj.core.groups.Tuple> zip(List<Long> courseIds, List<Double> lats, List<Double> lngs) {
        List<org.assertj.core.groups.Tuple> tuples = new ArrayList<>();
        for (int i = 0; i < courseIds.size(); i++) {
            tuples.add(tuple(courseIds.get(i), lats.get(i), lngs.get(i)));
        }
        return tuples;
    }

    /** 삭제 대상 러닝들을 준비한다 — 각 러닝은 인자로 준 코스에 순서대로 매달린다 (같은 코스 반복 가능) */
    private void givenRunningsOnCourses(List<Long> runningIds, Course... coursesOfEachRunning) {
        List<Running> runningsToDelete = new ArrayList<>();
        for (Course course : coursesOfEachRunning) {
            Running running = mock(Running.class);
            when(running.getCourse()).thenReturn(course);
            runningsToDelete.add(running);
        }
        when(runningRepository.findByIds(runningIds)).thenReturn(runningsToDelete);
    }

    /**
     * LAZY Course 프록시를 흉내 낸다 — 식별자 게터는 초기화 없이 동작하지만,
     * 영속성 컨텍스트가 비워진 뒤의 초기화(좌표 접근)는 LazyInitializationException 이다.
     */
    private Course lazyCourse(long courseId, double startLat, double startLng,
                              AtomicBoolean persistenceContextCleared) {
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);  // 식별자 게터는 프록시를 초기화하지 않는다
        when(course.getStartCoordinate()).thenAnswer(invocation -> {
            if (persistenceContextCleared.get()) {
                throw new LazyInitializationException(
                        "could not initialize proxy [Course#" + courseId + "] - no Session");
            }
            return Coordinate.of(startLat, startLng);
        });
        return course;
    }

    @Test
    @DisplayName("updateRunningPublicStatus: 공개 여부가 바뀐 러닝의 코스를 재계산하고, 지도 셀 이빅트를 예약한다")
    void updateRunningPublicStatus_recalculatesCourse() {
        // given
        Long runningId = 11L;
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(30L);

        Running running = mock(Running.class);
        when(running.getCourse()).thenReturn(course);
        when(runningQueryService.findRunningByRunningId(runningId)).thenReturn(running);

        RunUpdatedEvent updatedEvent = new RunUpdatedEvent(runningId, 30L, memberUuid, "러닝", true);
        when(running.createUpdatedEvent()).thenReturn(updatedEvent);

        // when
        sut.updateRunningPublicStatus(runningId, memberUuid);

        // then
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(courseReadModelWriter, times(1)).recalculate(captor.capture());
        assertThat(captor.getValue()).containsExactly(30L);

        // 지도 셀 이빅트는 직접 호출로 예약된다
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(30L);
        // 이 발행은 구경로 캐시 무효화가 소비한다 — 구경로가 사라질 때까지는 함께 지우면 안 된다.
        verify(applicationEventPublisher).publishEvent(updatedEvent);
    }

    private CreateRunCommand publicRunCommand(long durationSeconds, boolean hasPaused) {
        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);
        when(cmd.getMode()).thenReturn("NORMAL");
        when(cmd.getIsPublic()).thenReturn(true);
        when(cmd.getHasPaused()).thenReturn(hasPaused);
        when(cmd.getRecord()).thenReturn(
                new RunRecordCommand(5.2, 40.0, -20.0, durationSeconds, 6.1, 302, 120, 56));
        return cmd;
    }

    /** 집계 대상 러닝 (공개 + 일시정지 아님) */
    private Running savedPublicRunning(long runningId, long durationSeconds, Member member, Course course) {
        return savedRunning(runningId, durationSeconds, true, false, member, course);
    }

    /** 집계 제외 러닝 (공개지만 일시정지함) */
    private Running savedPausedRunning(long runningId, long durationSeconds, Member member, Course course) {
        return savedRunning(runningId, durationSeconds, true, true, member, course);
    }

    private Running savedRunning(long runningId, long durationSeconds, boolean isPublic, boolean hasPaused,
                                 Member member, Course course) {
        Running running = mock(Running.class);
        when(running.getId()).thenReturn(runningId);
        when(running.isPublic()).thenReturn(isPublic);
        when(running.isHasPaused()).thenReturn(hasPaused);
        when(running.getMember()).thenReturn(member);
        when(running.getCourse()).thenReturn(course);
        when(running.getRunningRecord()).thenReturn(
                RunningRecord.of(5.2, 30.0, 40.0, -20.0, 6.1, 4.9, 6.9, durationSeconds, 302, 120, 56));
        return running;
    }

    // ====== 예외 가드(한 예시) ======

    @Test
    @DisplayName("createRun: 멤버 미존재 시 하위 호출 없이 예외 전파")
    void createRun_memberNotFound_propagatesAndNoSideEffects() {
        // given
        when(memberService.findMemberByUuid(memberUuid))
                .thenThrow(new RuntimeException("member not found"));

        CreateRunCommand cmd = mock(CreateRunCommand.class);

        // when/then
        assertThatThrownBy(() -> sut.createRun(cmd, memberUuid, 1L, raw(), interp(), shot()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("member not found");

        verifyNoInteractions(courseService, telemetryProcessor, runningFileUploader, mapper, runningRepository);
    }
}
