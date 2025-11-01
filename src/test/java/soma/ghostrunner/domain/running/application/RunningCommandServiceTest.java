package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.path.*;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

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
    @Mock RunningRepository runningRepository;
    @Mock TelemetryProcessor telemetryProcessor;
    @Mock RunningFileUploader runningFileUploader;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock PathSimplificationService pathSimplificationService;
    @Mock RunningQueryService runningQueryService;
    @Mock CourseService courseService;
    @Mock MemberService memberService;

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
                pathSimplificationService, runningQueryService, courseService, memberService
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
        when(mapper.toCourse(eq(member), eq(cmd), eq(stats), any(RunningDataUrlsDto.class))).thenReturn(course);

        Running running = mock(Running.class);
        when(mapper.toRunning(eq(cmd), eq(stats), any(RunningDataUrlsDto.class), eq(member), eq(course))).thenReturn(running);
        when(runningRepository.save(running)).thenReturn(running);

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
        inOrder.verify(mapper).toResponse(running, course);

        verifyNoMoreInteractions(memberService, telemetryProcessor, pathSimplificationService,
                runningFileUploader, courseService, runningRepository, mapper);
    }

    // ====== createRun (코스에 붙여 저장) ======

    @Test
    @DisplayName("createRun (NORMAL 모드): 코스 조회 → 보간처리 → 업로드(원시/보간/스크린샷) → 러닝 저장 → ID 반환")
    void createRun_normal_success() {
        // given
        long courseId = 77L;
        Course course = mock(Course.class);
        when(courseService.findCourseById(courseId)).thenReturn(course);

        Member member = Member.of("러너", "profile");
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        var stats = statsMock();
        when(telemetryProcessor.process(any(MultipartFile.class), eq(startedAt))).thenReturn(stats);

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
        Course course = mock(Course.class);
        when(courseService.findCourseById(courseId)).thenReturn(course);

        Member member = Member.of("러너", "profile");
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        var stats = statsMock();
        when(telemetryProcessor.process(any(MultipartFile.class), eq(startedAt))).thenReturn(stats);

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

        // when
        Long id = sut.createRun(cmd, memberUuid, courseId, raw(), interp(), shot());

        // then
        assertThat(id).isEqualTo(200L);
        verify(ghost).validateBelongsToCourse(courseId);
        verify(runningQueryService).findRunningByRunningId(999L);
    }

    // ====== 업데이트 계열 ======

    @Test
    @DisplayName("updateRunningName: 본인 검증 후 이름을 변경한다")
    void updateRunningName_updatesAfterOwnershipCheck() {
        // given
        Long runningId = 10L;
        Running running = mock(Running.class);
        when(runningQueryService.findRunningByRunningId(runningId)).thenReturn(running);

        // when
        sut.updateRunningName("새 이름", runningId, memberUuid);

        // then
        InOrder inOrder = inOrder(runningQueryService, running);
        inOrder.verify(runningQueryService).findRunningByRunningId(runningId);
        inOrder.verify(running).verifyMember(memberUuid);
        inOrder.verify(running).updateName("새 이름");
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
