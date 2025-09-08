package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
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
import soma.ghostrunner.domain.running.application.support.RunningDataUploader;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.application.support.TelemetryProcessor;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final RunningApplicationMapper mapper;

    private final RunningRepository runningRepository;
    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;

    private final TelemetryProcessor telemetryProcessor;
    private final RunningDataUploader runningDataUploader;

    private final PathSimplificationService pathSimplificationService;
    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;

    @Transactional
    public CreateCourseAndRunResponse createRunAndCourse(
            CreateRunCommand command, String memberUuid,
            MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        ProcessedTelemetriesDto processedTelemetries = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        SimplifiedPathDto simplifiedPath = pathSimplificationService.simplify(processedTelemetries);

        RunningDataUrlsDto runningDataUrlsDto = runningDataUploader.uploadAll(
                rawTelemetry, processedTelemetries, simplifiedPath, screenShotImage, memberUuid);

        Course course = createAndSaveCourse(member, command, processedTelemetries, runningDataUrlsDto);
        Running running = createAndSaveRunning(command, processedTelemetries, runningDataUrlsDto, member, course);
        return mapper.toResponse(running, course);
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

    private Course createAndSaveCourse(Member member, CreateRunCommand command,
                                       ProcessedTelemetriesDto processedTelemetriesDto,
                                       RunningDataUrlsDto runningDataUrlsDto) {
        Course course = mapper.toCourse(member, command, processedTelemetriesDto, runningDataUrlsDto);
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
        ProcessedTelemetriesDto processedTelemetries = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());

        RunningDataUrlsDto runningDataUrlsDto = runningDataUploader.uploadAll(rawTelemetry, processedTelemetries, screenShotImage, memberUuid);
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
        runningRepository.deleteInRunningIds(runningIds);
    }

}
