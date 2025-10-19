package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.path.TelemetryProcessor;
import soma.ghostrunner.domain.running.domain.path.RunningFileUploader;
import soma.ghostrunner.domain.running.domain.path.SimplifiedPaths;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;

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

    @Transactional
    public CreateCourseAndRunResponse createRunAndCourse(
            CreateRunCommand command, String memberUuid,
            MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        TelemetryStatistics telemetryStatistics = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        SimplifiedPaths simplifiedPaths = pathSimplificationService.simplify(telemetryStatistics);

        RunningDataUrlsDto dataUrlsDto = upload(rawTelemetry, telemetryStatistics, simplifiedPaths, screenShotImage, member);

        Course course = createAndSaveCourse(member, command, telemetryStatistics, dataUrlsDto);
        Running running = createAndSaveRunning(command, telemetryStatistics, dataUrlsDto, member, course);
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
        return runningRepository.save(mapper.toRunning(command, telemetryStatistics, runningDataUrlsDto, member, course));
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

        eventPublisher.publishEvent(mapper.toCourseRunEvent(running, course, member));
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
    }

    @Transactional
    public void updateRunningPublicStatus(Long runningId, String memberUuid) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        running.updatePublicStatus();
    }

    private Running findRunning(Long runningId) {
        return runningQueryService.findRunningByRunningId(runningId);
    }

    @Transactional
    public void deleteRunnings(List<Long> runningIds, String memberUuid) {
        List<Running> runnings = runningRepository.findByIds(runningIds);
        runnings.forEach(running -> running.verifyMember(memberUuid));
        runningRepository.deleteInRunningIds(runningIds);
    }

}
