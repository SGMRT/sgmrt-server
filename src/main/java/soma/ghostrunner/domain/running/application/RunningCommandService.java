package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.application.MemberVdotWriter;
import soma.ghostrunner.domain.course.application.CourseSubscriptionService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.SimplifiedPaths;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final RunningApplicationMapper mapper;

    private final RunningRepository runningRepository;

    private final TelemetryProcessor telemetryProcessor;
    private final RunningFileUploader runningFileUploader;
    private final ApplicationEventPublisher eventPublisher;

    private final PathSimplificationService pathSimplificationService;
    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;
    private final CourseReadModelWriter courseReadModelWriter;
    private final MemberVdotWriter memberVdotWriter;
    private final CourseSubscriptionService courseSubscriptionService;

    @Transactional
    public CreateCourseAndRunResponse createRunAndCourse(
            CreateRunCommand command, String memberUuid,
            MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        // TODO: 러닝 데이터 가공해서 S3에 업로드하는 것은 비동기로 빼는 것 고민하기
        TelemetryStatistics telemetryStatistics = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        SimplifiedPaths simplifiedPaths = pathSimplificationService.simplify(telemetryStatistics);
        RunningDataUrlsDto dataUrlsDto = upload(rawTelemetry, telemetryStatistics, simplifiedPaths, screenShotImage, member);

        Course course = createAndSaveCourse(member, command, telemetryStatistics, dataUrlsDto);
        Running running = createAndSaveRunning(command, telemetryStatistics, dataUrlsDto, member, course);

        courseReadModelWriter.applyRun(running);   // 집계 대상 판정은 Writer 책임 (신규 코스는 리드모델 부재로 내부 스킵)
        memberVdotWriter.updateFromRun(member.getUuid(), running.getRunningRecord().getAveragePace());
        eventPublisher.publishEvent(running.createFinishedEvent());   // 소비자: 코스 캐시 무효화(AFTER_COMMIT)만
        return mapper.toResponse(running, course);
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
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

    private Course createAndSaveCourse(Member member, CreateRunCommand command,
                                       TelemetryStatistics telemetryStatistics,
                                       RunningDataUrlsDto runningDataUrlsDto) {
        Course course = mapper.toCourse(member, command, telemetryStatistics, runningDataUrlsDto);
        courseService.save(course);
        return course;
    }

    private Running createAndSaveRunning(CreateRunCommand command, TelemetryStatistics telemetryStatistics,
                                         RunningDataUrlsDto runningDataUrlsDto, Member member, Course course) {
        Running running = mapper.toRunning(command, telemetryStatistics, runningDataUrlsDto, member, course);
        return runningRepository.save(running);
    }

    @Transactional
    public Long createRun(CreateRunCommand command, String memberUuid, Long courseId,
                          MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);
        Course course = findCourse(courseId);

        validateBelongsToCourseIfGhostMode(command, courseId);
        TelemetryStatistics processedTelemetries = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());

        RunningDataUrlsDto runningDataUrlsDto = upload(rawTelemetry, processedTelemetries, screenShotImage, member);
        Running running = createAndSaveRunning(command, processedTelemetries, runningDataUrlsDto, member, course);

        courseReadModelWriter.applyRun(running);   // 집계 대상 판정은 Writer 책임
        memberVdotWriter.updateFromRun(member.getUuid(), running.getRunningRecord().getAveragePace());
        courseSubscriptionService.subscribeIfAbsent(courseId, member.getId());
        publishCourseRunEvents(running);
        return running.getId();
    }

    /**
     * 코스를 따라 뛴 러닝의 종료 이벤트를 발행한다. (남은 소비자는 전부 AFTER_COMMIT 부수효과)
     *
     * - RunFinishedEvent → 코스 캐시 무효화(CourseCacheEventListener) — 구경로 캐시 제거 시 함께 삭제 예정
     * - CourseRunEvent   → 푸시 발송(PushEventListener)
     *
     * VDOT 갱신·구독 생성은 같은 트랜잭션 동기 로직이라 직접 호출로 전환됨 (설계 04 §6).
     */
    private void publishCourseRunEvents(Running running) {
        eventPublisher.publishEvent(running.createFinishedEvent());
        eventPublisher.publishEvent(running.createCourseRunEvent());
    }

    private RunningDataUrlsDto upload(MultipartFile rawTelemetry, TelemetryStatistics telemetryStatistics,
                                      MultipartFile screenShotImage, Member member) {

        String rawUrl = runningFileUploader.uploadRawTelemetry(rawTelemetry, member.getUuid());
        String interpolatedUrl = runningFileUploader.uploadInterpolatedTelemetry(telemetryStatistics.relativeTelemetries(), member.getUuid());
        String screenShotUrl = runningFileUploader.uploadRunningCaptureImage(screenShotImage, member.getUuid());

        return new RunningDataUrlsDto(rawUrl, interpolatedUrl, screenShotUrl);
    }

    private Course findCourse(Long courseId) {
        return courseService.findCourseByIdFetchJoinMember(courseId);
    }

    private void validateBelongsToCourseIfGhostMode(CreateRunCommand command, Long courseId) {
        if (command.getMode().equals("GHOST")) {
            Running ghostRunning = findRunning(command.getGhostRunningId());
            ghostRunning.validateBelongsToCourse(courseId);
        }
    }

    @Transactional
    public void updateRunningName(String name, Long runningId, String memberUuid) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        running.updateName(name);
        // RunUpdatedEvent → 코스 캐시 무효화(CourseCacheEventListener)
        eventPublisher.publishEvent(running.createUpdatedEvent());
    }

    @Transactional
    public void updateRunningPublicStatus(Long runningId, String memberUuid) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        running.updatePublicStatus();
        // RunUpdatedEvent → 코스 캐시 무효화(CourseCacheEventListener)
        eventPublisher.publishEvent(running.createUpdatedEvent());

        // 공개 여부가 바뀌면 집계 모집단이 달라지므로 해당 코스의 리드모델을 다시 계산한다
        Course course = running.getCourse();
        if (course != null) {
            courseReadModelWriter.recalculate(List.of(course.getId()));
        }
    }

    private Running findRunning(Long runningId) {
        return runningQueryService.findRunningByRunningId(runningId);
    }

    @Transactional
    public void deleteRunnings(List<Long> runningIds, String memberUuid) {
        List<Running> runningsToDelete = runningRepository.findByIds(runningIds);
        runningsToDelete.forEach(running -> running.verifyMember(memberUuid));

        // 삭제 전에 모아둬야 어떤 코스의 리드모델을 다시 계산할지 알 수 있다
        List<Long> affectedCourseIds = distinctCourseIdsOf(runningsToDelete);

        runningRepository.deleteInRunningIds(runningIds);
        courseReadModelWriter.recalculate(affectedCourseIds);
    }

    /**
     * 러닝들이 속한 코스 ID 를 중복 없이 모은다. (코스에 속하지 않은 러닝은 제외)
     */
    private List<Long> distinctCourseIdsOf(List<Running> runnings) {
        return runnings.stream()
                .map(Running::getCourse)
                .filter(Objects::nonNull)
                .map(Course::getId)
                .distinct()
                .toList();
    }

}
