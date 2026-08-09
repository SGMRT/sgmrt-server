package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseQueryService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.RunningWriter.CreatedRun;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.application.MemberVdotWriter;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.SimplifiedPaths;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;

/**
 * 러닝 쓰기 유즈케이스의 조율자. <b>이 클래스는 트랜잭션을 열지 않는다</b> —
 * DB 쓰기 경계는 전부 {@link RunningWriter}가 갖는다.
 *
 * <p><b>러닝 생성이 조율의 핵심이다.</b> 시계열 가공(CPU)과 S3 업로드(네트워크 I/O)를 DB 커넥션을 쥔 채
 * 수행하면 동시 요청이 늘 때 커넥션 풀이 먼저 마르기 때문에, 순서는
 * "조회·가공·업로드 → 저장 트랜잭션 → VDOT"이다.
 *
 * <p>수정·삭제 계열({@code updateRunningName}, {@code updateRunningPublicStatus}, {@code deleteRunnings})은
 * 트랜잭션 밖 조율이 필요 없어 {@link RunningWriter}에 그대로 위임한다. 진입점을 이 클래스로 통일해
 * API 계층은 Writer의 존재를 모르게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final RunningApplicationMapper mapper;

    private final TelemetryProcessor telemetryProcessor;
    private final RunningFileUploader runningFileUploader;

    private final PathSimplificationService pathSimplificationService;
    private final RunningQueryService runningQueryService;
    private final CourseQueryService courseQueryService;
    private final MemberService memberService;
    private final MemberVdotWriter memberVdotWriter;
    private final RunningWriter runningWriter;

    public CreateCourseAndRunResponse createRunAndCourse(
            CreateRunCommand command, String memberUuid, MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        // TODO: 러닝 데이터 가공해서 S3에 업로드하는 것은 비동기로 빼는 것 고민하기
        TelemetryStatistics telemetryStatistics = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        SimplifiedPaths simplifiedPaths = pathSimplificationService.simplify(telemetryStatistics);
        RunningDataUrlsDto dataUrlsDto = upload(rawTelemetry, telemetryStatistics, simplifiedPaths, screenShotImage, member);

        CreatedRun created = runningWriter.saveRunAndCourse(command, member, telemetryStatistics, dataUrlsDto);
        updateVdotOrAlert(memberUuid, created.running());
        return mapper.toResponse(created.running(), created.course());
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

    /**
     * VDOT를 갱신하되, 실패해도 이미 커밋된 러닝을 되돌리지 않는다.
     *
     * <p>VDOT 원자성을 포기한 이유는 손해의 비대칭이다 — 사용자가 방금 뛴 기록이 통째로 사라지는 것보다,
     * VDOT만 낡은 채 두고(다음 러닝에서 재계산된다) 개발자가 인지하는 편이 낫다.
     *
     * <p>별도의 알림 채널을 두지 않는 이유는 {@code logback-spring.xml}에 있다 — {@code SentryAppender}가
     * ERROR {@code ThresholdFilter}와 함께 prod 프로파일 root 에 물려 있어, 운영에서 {@code log.error}는
     * 그 자체로 Sentry 알럿이 된다. (warn 이 아니라 error 인 이유 — 이 저장소 관례상 warn 은
     * "TTL 안에 자가 치유되는 것", error 는 "데이터가 실제로 어긋난 채 남아 사람이 봐야 하는 것"이다.)
     */
    private void updateVdotOrAlert(String memberUuid, Running running) {
        try {
            memberVdotWriter.updateFromRun(memberUuid, running.getRunningRecord().getAveragePace());
        } catch (Exception e) {
            log.error("VDOT 갱신 실패, 러닝은 정상 저장됨 - memberUuid={}, runningId={}", memberUuid, running.getId(), e);
        }
    }

    private RunningDataUrlsDto upload(MultipartFile rawTelemetry, TelemetryStatistics telemetryStatistics,
                                      SimplifiedPaths simplifiedPaths, MultipartFile screenShotImage, Member member) {

        String rawUrl = runningFileUploader.uploadRawTelemetry(rawTelemetry, member.getUuid());
        String interpolatedUrl = runningFileUploader.uploadInterpolatedTelemetry(telemetryStatistics.relativeTelemetries(), member.getUuid());
        String simplifiedUrl = runningFileUploader.uploadSimplifiedCoordinates(simplifiedPaths.simplifiedCoordinates(), member.getUuid());
        String checkpointUrl = runningFileUploader.uploadCheckpoints(simplifiedPaths.checkpoints(), member.getUuid());
        String screenShotUrl = runningFileUploader.uploadRunningCaptureImage(screenShotImage, member.getUuid());

        return new RunningDataUrlsDto(rawUrl, interpolatedUrl, simplifiedUrl, checkpointUrl, screenShotUrl);
    }

    /**
     * 코스를 따라 뛴 러닝을 저장한다.
     *
     * <p>코스 조회와 GHOST 검증은 읽기라 트랜잭션 밖에 둔다 — 존재하지 않는 코스/다른 코스의 고스트라면
     * S3 업로드를 시작하기 전에 실패하는 편이 낫다(fail-fast). 저장에 쓸 코스는
     * detached 를 피하려고 {@code courseId}만 넘겨 트랜잭션 안에서 다시 조회한다.
     */
    public Long createRun(CreateRunCommand command, String memberUuid, Long courseId,
                          MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);
        findCourse(courseId);  // fail-fast — 없는 코스면 업로드 전에 끝낸다
        validateBelongsToCourseIfGhostMode(command, courseId);

        TelemetryStatistics processedTelemetries = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        RunningDataUrlsDto runningDataUrlsDto = upload(rawTelemetry, processedTelemetries, screenShotImage, member);

        Running running = runningWriter.saveRun(command, member, courseId, processedTelemetries, runningDataUrlsDto);

        updateVdotOrAlert(memberUuid, running);
        return running.getId();
    }

    private RunningDataUrlsDto upload(MultipartFile rawTelemetry, TelemetryStatistics telemetryStatistics,
                                      MultipartFile screenShotImage, Member member) {

        String rawUrl = runningFileUploader.uploadRawTelemetry(rawTelemetry, member.getUuid());
        String interpolatedUrl = runningFileUploader.uploadInterpolatedTelemetry(telemetryStatistics.relativeTelemetries(), member.getUuid());
        String screenShotUrl = runningFileUploader.uploadRunningCaptureImage(screenShotImage, member.getUuid());

        return new RunningDataUrlsDto(rawUrl, interpolatedUrl, screenShotUrl);
    }

    private Course findCourse(Long courseId) {
        return courseQueryService.findCourseByIdFetchJoinMember(courseId);
    }

    private void validateBelongsToCourseIfGhostMode(CreateRunCommand command, Long courseId) {
        if (command.getMode().equals("GHOST")) {
            Running ghostRunning = runningQueryService.findRunningByRunningId(command.getGhostRunningId());
            ghostRunning.validateBelongsToCourse(courseId);
        }
    }

    public void updateRunningName(String name, Long runningId, String memberUuid) {
        runningWriter.updateName(name, runningId, memberUuid);
    }

    public void updateRunningPublicStatus(Long runningId, String memberUuid) {
        runningWriter.updatePublicStatus(runningId, memberUuid);
    }

    public void deleteRunnings(List<Long> runningIds, String memberUuid) {
        runningWriter.deleteRunnings(runningIds, memberUuid);
    }

}
