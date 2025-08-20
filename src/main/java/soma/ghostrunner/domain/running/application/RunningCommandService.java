package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.support.TelemetryProcessor;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.PathSimplifier;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final RunningServiceMapper mapper;
    private final RunningRepository runningRepository;

    private final GhostRunnerS3Client ghostRunnerS3Client;

    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;

    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(
            CreateRunCommand command, String memberUuid,
            MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        ProcessedTelemetriesDto processedTelemetries = processTelemetries(interpolatedTelemetry, command);
        List<CoordinateDto> simplifiedCoordinates = simplifyToRenderingTelemetries(processedTelemetries);
        List<CheckpointDto> checkpoints =  simplifyToCheckpointTelemetries(processedTelemetries);

        RunningDataUrlsDto runningDataUrlsDto = saveRunningAndCourseDataToS3(
                rawTelemetry, processedTelemetries, simplifiedCoordinates, checkpoints, screenShotImage, memberUuid);

        Course course = createAndSaveCourse(member, command, processedTelemetries, runningDataUrlsDto);
        Running running = createAndSaveRunning(command, processedTelemetries, runningDataUrlsDto, member, course);
        return mapper.toResponse(running, course);
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

    private ProcessedTelemetriesDto processTelemetries(MultipartFile interpolatedTelemetry, CreateRunCommand command) {
        return TelemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
    }

    private List<CoordinateDto> simplifyToRenderingTelemetries(ProcessedTelemetriesDto processedTelemetries) {
        return PathSimplifier.simplifyToRenderingTelemetries(
                CoordinateDtoWithTs.toCoordinateDtosWithTsList(processedTelemetries.relativeTelemetries()));
    }

    private List<CheckpointDto> simplifyToCheckpointTelemetries(ProcessedTelemetriesDto processedTelemetries) {
        List<CoordinateDtoWithTs> telemetryCoordinates = processedTelemetries.relativeTelemetries().stream()
                .map(telemetry -> new CoordinateDtoWithTs(telemetry.getTimeStamp(), telemetry.getLat(), telemetry.getLng()))
                .toList();
        List<CoordinateDto> checkpoints = PathSimplifier.extractEdgePoints(telemetryCoordinates);
        return PathSimplifier.calculateAngles(checkpoints);
    }

    private RunningDataUrlsDto saveRunningAndCourseDataToS3(
            MultipartFile rawTelemetry, ProcessedTelemetriesDto processedTelemetries,
            List<CoordinateDto> simplifiedTelemetry, List<CheckpointDto> checkpointTelemetry,
            MultipartFile screenShotImage, String memberUuid) {

        RunningDataUrlsDto runningDataUrlsDto = saveRunningDataToS3(memberUuid, rawTelemetry, screenShotImage, processedTelemetries);
        String simplifiedTelemetrySavedUrl = ghostRunnerS3Client.uploadSimplifiedTelemetry(simplifiedTelemetry, memberUuid);
        runningDataUrlsDto.setSimplifiedPathSavedUrl(simplifiedTelemetrySavedUrl);
        String checkpointUrl = ghostRunnerS3Client.uploadCourseCheckpoint(checkpointTelemetry, memberUuid);
        runningDataUrlsDto.setCheckpointUrl(checkpointUrl);

        return runningDataUrlsDto;
    }

    private RunningDataUrlsDto saveRunningDataToS3(String memberUuid, MultipartFile rawTelemetry,
                                                    MultipartFile screenShotImage, ProcessedTelemetriesDto processedTelemetries) {
        String rawTelemetrySavedUrl = ghostRunnerS3Client.uploadRawTelemetry(rawTelemetry, memberUuid);
        String interpolatedTelemetrySavedUrl = ghostRunnerS3Client.uploadInterpolatedTelemetry(processedTelemetries.relativeTelemetries(), memberUuid);

        String screenShotSavedUrl = null;
        if (screenShotImage != null) {
            screenShotSavedUrl = uploadRunningScreenShotImage(memberUuid, screenShotImage);
        }

        return new RunningDataUrlsDto(rawTelemetrySavedUrl, interpolatedTelemetrySavedUrl, null, null, screenShotSavedUrl);
    }

    private Course createAndSaveCourse(Member member, CreateRunCommand command,
                                         ProcessedTelemetriesDto processedTelemetriesDto, RunningDataUrlsDto runningDataUrlsDto) {
        Course course = mapper.toCourse(member, command, processedTelemetriesDto,
                runningDataUrlsDto.getSimplifiedPathSavedUrl(), runningDataUrlsDto.getCheckpointUrl(), runningDataUrlsDto.getScreenShotUrl());
        courseService.save(course);
        return course;
    }

    private Running createAndSaveRunning(CreateRunCommand command, ProcessedTelemetriesDto processedTelemetry,
                                         RunningDataUrlsDto runningDataUrlsDto, Member member, Course course) {
        return runningRepository.save(mapper.toRunning(command, processedTelemetry, runningDataUrlsDto, member, course));
    }

    @Transactional
    public Long createRun(CreateRunCommand command, String memberUuid, Long courseId,
                          MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);
        Course course = findCourse(courseId);

        validateBelongsToCourseIfGhostMode(command, courseId);
        ProcessedTelemetriesDto processedTelemetries = processTelemetries(interpolatedTelemetry, command);

        RunningDataUrlsDto runningDataUrlsDto = saveRunningDataToS3(memberUuid, rawTelemetry, screenShotImage, processedTelemetries);
        Running running = createAndSaveRunning(command, processedTelemetries, runningDataUrlsDto, member, course);
        return running.getId();
    }

    private Course findCourse(Long courseId) {
        return courseService.findCourseById(courseId);
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
        runningRepository.deleteAllByIdIn(runningIds);
    }

    @Transactional
    public void updateScreenShotImage(String memberUuid, Long runningId, MultipartFile screenShotImage) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        String savedUrl = uploadRunningScreenShotImage(memberUuid, screenShotImage);
        running.updateScreenShotUrl(savedUrl);
    }

    private String uploadRunningScreenShotImage(String memberUuid, MultipartFile screenShotImage) {
        return ghostRunnerS3Client.uploadRunningCaptureImage(screenShotImage, memberUuid);
    }

}
