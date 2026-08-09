package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseQueryService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.application.MemberVdotWriter;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.domain.path.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class RunningCommandServiceTest {

    @Mock RunningApplicationMapper mapper;
    @Mock TelemetryProcessor telemetryProcessor;
    @Mock RunningFileUploader runningFileUploader;
    @Mock PathSimplificationService pathSimplificationService;
    @Mock RunningQueryService runningQueryService;
    @Mock CourseQueryService courseQueryService;
    @Mock MemberService memberService;
    @Mock MemberVdotWriter memberVdotWriter;
    @Mock RunningWriter runningWriter;

    RunningCommandService sut;

    // 고정 픽스처
    private final String memberUuid = "mem-123";
    private final long startedAt = LocalDateTime.of(2025, 8, 1, 7, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli();

    @BeforeEach
    void setUp() {
        sut = new RunningCommandService(
                mapper, telemetryProcessor, runningFileUploader,
                pathSimplificationService, runningQueryService, courseQueryService, memberService,
                memberVdotWriter, runningWriter
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
        when(courseQueryService.findCourseByIdFetchJoinMember(courseId)).thenReturn(course);
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

    /**
     * 이 순서가 이 클래스의 존재 이유다 — <b>가공·업로드는 저장 트랜잭션보다 앞, VDOT는 뒤.</b>
     * 저장 트랜잭션(= {@link RunningWriter})은 DB 커넥션을 쥐고 있으므로, 그 안에서 S3 왕복 5회를
     * 하면 동시 요청이 늘 때 커넥션 풀이 먼저 마른다. 업로드를 writer 뒤로 옮기면 이 테스트가 빨개진다.
     */
    @Test
    @DisplayName("createRunAndCourse: 회원 조회 → 가공 → S3 업로드 → 저장 트랜잭션 → VDOT → 응답 매핑 순서로 조율한다")
    void createRunAndCourse_success_orchestration() {
        // given
        Member member = mock(Member.class);
        when(member.getUuid()).thenReturn(memberUuid); // 업로드 경로가 member.getUuid()를 사용함
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

        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);

        Course course = mock(Course.class);
        Running running = savedRunningWithRecord(1L);
        when(runningWriter.saveRunAndCourse(eq(cmd), eq(member), eq(stats), any(RunningDataUrlsDto.class)))
                .thenReturn(new RunningWriter.CreatedRun(running, course));

        CreateCourseAndRunResponse response = new CreateCourseAndRunResponse(null, null);
        when(mapper.toResponse(running, course)).thenReturn(response);

        // when
        CreateCourseAndRunResponse result =
                sut.createRunAndCourse(cmd, memberUuid, raw(), interp(), shot());

        // then
        assertThat(result).isSameAs(response);

        InOrder inOrder = inOrder(memberService, telemetryProcessor, pathSimplificationService,
                runningFileUploader, runningWriter, memberVdotWriter, mapper);

        inOrder.verify(memberService).findMemberByUuid(memberUuid);
        inOrder.verify(telemetryProcessor).process(any(MultipartFile.class), eq(startedAt));
        inOrder.verify(pathSimplificationService).simplify(stats);
        inOrder.verify(runningFileUploader).uploadRawTelemetry(any(), eq(memberUuid));
        inOrder.verify(runningFileUploader).uploadRunningCaptureImage(any(), eq(memberUuid));
        inOrder.verify(runningWriter)
                .saveRunAndCourse(eq(cmd), eq(member), eq(stats), any(RunningDataUrlsDto.class));
        // VDOT는 러닝이 커밋된 뒤에 갱신한다 (실패해도 러닝을 롤백시키지 않기 위해)
        inOrder.verify(memberVdotWriter).updateFromRun(eq(memberUuid), any());
        inOrder.verify(mapper).toResponse(running, course);

        verify(runningFileUploader).uploadInterpolatedTelemetry(anyList(), eq(memberUuid));
        verify(runningFileUploader).uploadSimplifiedCoordinates(anyList(), eq(memberUuid));
        verify(runningFileUploader).uploadCheckpoints(anyList(), eq(memberUuid));

        // 저장·리드모델·이빅트는 전부 writer 의 트랜잭션 안 책임이다 — 서비스가 직접 하지 않는다
        verifyNoInteractions(courseQueryService);
    }

    /**
     * VDOT 원자성을 포기한 대가로 반드시 지켜야 할 계약 — <b>VDOT 실패가 러닝 종료 API를 실패시키지 않는다.</b>
     * (러닝이 실제로 커밋된다는 사실은 통합 테스트가 검증한다)
     */
    @Test
    @DisplayName("createRun: VDOT 갱신이 실패해도 예외를 전파하지 않고 러닝 ID를 반환한다")
    void createRun_vdotFailure_stillReturnsRunningId() {
        // given
        long courseId = 77L;
        givenFoundCourse(courseId);
        Member member = givenFoundRunner(5L);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);
        when(cmd.getMode()).thenReturn("NORMAL");

        Running running = savedRunningWithRecord(100L);
        when(runningWriter.saveRun(eq(cmd), eq(member), eq(courseId), eq(stats), any(RunningDataUrlsDto.class)))
                .thenReturn(running);
        doThrow(new RuntimeException("vdot down"))
                .when(memberVdotWriter).updateFromRun(eq(memberUuid), any());

        // when
        Long id = sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then
        assertThat(id).isEqualTo(100L);
        verify(memberVdotWriter).updateFromRun(eq(memberUuid), any());
    }

    // ====== createRun (코스에 붙여 저장) ======

    /**
     * 저장에 넘기는 것은 {@code Course} 엔티티가 아니라 <b>{@code courseId}</b> 다. 트랜잭션 밖에서 읽은
     * 코스는 detached 라, 그대로 넘기면 저장 트랜잭션 안의 LAZY 접근에서 터진다.
     * (여기서 코스를 미리 읽는 이유는 fail-fast 뿐이다 — 없는 코스에 S3 업로드부터 하지 않기 위해)
     */
    @Test
    @DisplayName("createRun (NORMAL 모드): 코스 존재 확인 → 보간처리 → 업로드 → 저장 트랜잭션에 courseId 를 넘긴다")
    void createRun_normal_success() {
        // given
        long courseId = 77L;
        givenFoundCourse(courseId);
        Member member = givenFoundRunner(5L);
        TelemetryStatistics stats = givenProcessedTelemetry();

        when(runningFileUploader.uploadRawTelemetry(any(), eq(memberUuid))).thenReturn("s3://raw");
        when(runningFileUploader.uploadInterpolatedTelemetry(anyList(), eq(memberUuid))).thenReturn("s3://interp");
        when(runningFileUploader.uploadRunningCaptureImage(any(), eq(memberUuid))).thenReturn("s3://shot");

        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);
        when(cmd.getMode()).thenReturn("NORMAL");

        Running running = savedRunningWithRecord(100L);
        when(runningWriter.saveRun(eq(cmd), eq(member), eq(courseId), eq(stats), any(RunningDataUrlsDto.class)))
                .thenReturn(running);

        // when
        Long id = sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then
        assertThat(id).isEqualTo(100L);
        verify(runningQueryService, never()).findRunningByRunningId(anyLong());

        // 업로드가 저장 트랜잭션보다 먼저다 (커넥션을 쥔 채 S3 왕복 금지)
        InOrder inOrder = inOrder(runningFileUploader, runningWriter);
        inOrder.verify(runningFileUploader).uploadRunningCaptureImage(any(), eq(memberUuid));
        inOrder.verify(runningWriter)
                .saveRun(eq(cmd), eq(member), eq(courseId), eq(stats), any(RunningDataUrlsDto.class));
    }

    @Test
    @DisplayName("createRun (GHOST 모드): 고스트 러닝이 같은 코스에 속하는지 업로드 전에 검증한다")
    void createRun_ghostMode_validatesBelongsToCourse() {
        // given
        long courseId = 88L;
        givenFoundCourse(courseId);
        Member member = givenFoundRunner(5L);
        TelemetryStatistics stats = givenProcessedTelemetry();

        CreateRunCommand cmd = mock(CreateRunCommand.class);
        when(cmd.getStartedAt()).thenReturn(startedAt);
        when(cmd.getMode()).thenReturn("GHOST");
        when(cmd.getGhostRunningId()).thenReturn(999L);

        Running ghost = mock(Running.class);
        when(runningQueryService.findRunningByRunningId(999L)).thenReturn(ghost);

        Running running = savedRunningWithRecord(200L);
        when(runningWriter.saveRun(eq(cmd), eq(member), eq(courseId), eq(stats), any(RunningDataUrlsDto.class)))
                .thenReturn(running);

        // when
        Long id = sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then
        assertThat(id).isEqualTo(200L);
        verify(ghost).validateBelongsToCourse(courseId);
        verify(runningQueryService).findRunningByRunningId(999L);

        InOrder inOrder = inOrder(ghost, runningFileUploader);
        inOrder.verify(ghost).validateBelongsToCourse(courseId);
        inOrder.verify(runningFileUploader).uploadRawTelemetry(any(), eq(memberUuid));
    }

    /** 저장 트랜잭션이 반환한 러닝 — 서비스는 ID와 평균 페이스만 읽는다. */
    private Running savedRunningWithRecord(long runningId) {
        Running running = mock(Running.class);
        when(running.getId()).thenReturn(runningId);
        when(running.getRunningRecord()).thenReturn(
                RunningRecord.of(5.2, 30.0, 40.0, -20.0, 6.1, 4.9, 6.9, 1800L, 302, 120, 56));
        return running;
    }

    // ====== 수정·삭제 — Writer 위임 ======
    // 수정·삭제 트랜잭션의 내부 동작(소유자 검증, 리드모델 재계산, 지도 셀 이빅트)은 쓰기 경계인
    // RunningWriter 의 책임이라 RunningWriterTest 가 검증한다. 여기서는 위임만 본다.

    @Test
    @DisplayName("updateRunningName: RunningWriter 에 그대로 위임한다")
    void updateRunningName_delegatesToWriter() {
        // when
        sut.updateRunningName("새 이름", 10L, memberUuid);

        // then
        verify(runningWriter).updateName("새 이름", 10L, memberUuid);
        verifyNoMoreInteractions(runningWriter);
    }

    @Test
    @DisplayName("updateRunningPublicStatus: RunningWriter 에 그대로 위임한다")
    void updateRunningPublicStatus_delegatesToWriter() {
        // when
        sut.updateRunningPublicStatus(11L, memberUuid);

        // then
        verify(runningWriter).updatePublicStatus(11L, memberUuid);
        verifyNoMoreInteractions(runningWriter);
    }

    @Test
    @DisplayName("deleteRunnings: RunningWriter 에 그대로 위임한다")
    void deleteRunnings_delegatesToWriter() {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        verify(runningWriter).deleteRunnings(ids, memberUuid);
        verifyNoMoreInteractions(runningWriter);
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

        verifyNoInteractions(courseQueryService, telemetryProcessor, runningFileUploader, mapper, runningWriter);
    }
}
