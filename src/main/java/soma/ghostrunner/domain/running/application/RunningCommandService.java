package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.support.TelemetryProcessor;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.PathSimplifier;
import soma.ghostrunner.domain.running.domain.Running;

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

    // TODO : 러닝 저장 시 이벤트 발행, S3에 업로드 후 DB I/O 실패했을 때 S3의 유령 데이터 대응책 필요
    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(
            CreateRunCommand command, String memberUuid,
            MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        ProcessedTelemetriesDto processedTelemetries = processTelemetries(interpolatedTelemetry);
        List<CoordinateDto> simplifiedCoordinates = simplifyCoordinates(processedTelemetries);

        RunningDataUrlsDto runningDataUrlsDto = saveRunningAndCourseDataToS3(
                rawTelemetry, processedTelemetries, simplifiedCoordinates, screenShotImage, memberUuid);

        Course course = createAndSaveCourse(member, command, processedTelemetries, runningDataUrlsDto.simplifiedPathSavedUrl());
        Running running = createAndSaveRunning(command, processedTelemetries, runningDataUrlsDto, member, course);
        return CreateCourseAndRunResponse.of(running.getId(), course.getId());
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

    private ProcessedTelemetriesDto processTelemetries(MultipartFile interpolatedTelemetry) {
        return TelemetryProcessor.process(interpolatedTelemetry);
    }

    private List<CoordinateDto> simplifyCoordinates(ProcessedTelemetriesDto processedTelemetries) {
        return PathSimplifier.simplify(processedTelemetries.coordinates());
    }

    private RunningDataUrlsDto saveRunningAndCourseDataToS3(
            MultipartFile rawTelemetry, ProcessedTelemetriesDto processedTelemetries,
            List<CoordinateDto> simplifiedTelemetry, MultipartFile screenShotImage, String memberUuid) {

        String rawTelemetrySavedUrl = ghostRunnerS3Client.uploadRawTelemetry(rawTelemetry, memberUuid);
        String interpolatedTelemetrySavedUrl = ghostRunnerS3Client.uploadInterpolatedTelemetry(processedTelemetries.relativeTelemetries(), memberUuid);
        String simplifiedTelemetrySavedUrl = ghostRunnerS3Client.uploadSimplifiedTelemetry(simplifiedTelemetry, memberUuid);
        String screenShotSavedUrl = ghostRunnerS3Client.uploadMemberProfileImage(screenShotImage, memberUuid);

        return new RunningDataUrlsDto(rawTelemetrySavedUrl, interpolatedTelemetrySavedUrl, simplifiedTelemetrySavedUrl, screenShotSavedUrl);
    }

    private Course createAndSaveCourse(Member member, CreateRunCommand command,
                                         ProcessedTelemetriesDto processedTelemetriesDto, String pathDataSavedUrl) {
        Course course = mapper.toCourse(member, command, processedTelemetriesDto, pathDataSavedUrl);
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
        ProcessedTelemetriesDto processedTelemetries = processTelemetries(interpolatedTelemetry);

        RunningDataUrlsDto runningDataUrlsDto = saveRunningDataToS3(memberUuid, rawTelemetry, screenShotImage, processedTelemetries);
        Running running = createAndSaveRunning(command, processedTelemetries, runningDataUrlsDto, member, course);
        return running.getId();
    }

    private Course findCourse(Long courseId) {
        return courseService.findCourseById(courseId);
    }

    private void validateBelongsToCourseIfGhostMode(CreateRunCommand command, Long courseId) {
        if (command.mode().equals("GHOST")) {
            Running ghostRunning = findRunning(command.ghostRunningId());
            ghostRunning.validateBelongsToCourse(courseId);
        }
    }

    private RunningDataUrlsDto saveRunningDataToS3(String memberUuid, MultipartFile rawTelemetry,
                                                   MultipartFile screenShotImage, ProcessedTelemetriesDto processedTelemetries) {
        String rawTelemetrySavedUrl = ghostRunnerS3Client.uploadRawTelemetry(rawTelemetry, memberUuid);
        String interpolatedTelemetrySavedUrl = ghostRunnerS3Client.uploadInterpolatedTelemetry(processedTelemetries.relativeTelemetries(), memberUuid);
        String screenShotSavedUrl = ghostRunnerS3Client.uploadMemberProfileImage(screenShotImage, memberUuid);
        return new RunningDataUrlsDto(rawTelemetrySavedUrl, interpolatedTelemetrySavedUrl, null, screenShotSavedUrl);
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

}
