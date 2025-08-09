package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.upload.S3TelemetryClient;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordDto;
import soma.ghostrunner.domain.running.domain.support.TelemetryCalculator;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.domain.support.TelemetryTypeConverter;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final S3TelemetryClient s3TelemetryClient;
    private final RunningRepository runningRepository;

    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;

    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(CreateRunCommand command, String memberUuid) {
        Member member = memberService.findMemberByUuid(memberUuid);

        ProcessedTelemetriesDto processedTelemetry = processTelemetry(command);
        String url = uploadTelemetryToS3(memberUuid, processedTelemetry);

        Course course = createAndSaveCourse(member, command, processedTelemetry);
        Running running = createAndSaveRunning(command, processedTelemetry, url, member, course);

        return CreateCourseAndRunResponse.of(running.getId(), course.getId());
    }

    @Transactional
    public Long createRun(CreateRunCommand command, Long courseId, String memberUuid) {
        Member member = memberService.findMemberByUuid(memberUuid);
        verifyCourseIdIfGhostMode(command, courseId);

        ProcessedTelemetriesDto processedTelemetry = processTelemetry(command);
        String url = uploadTelemetryToS3(memberUuid, processedTelemetry);

        Course course = courseService.findCourseById(courseId);
        Running running = createAndSaveRunning(command, processedTelemetry, url, member, course);
        return running.getId();
    }

    private void verifyCourseIdIfGhostMode(CreateRunCommand command, Long courseId) {
        if (command.mode().equals("GHOST")) {
            Running ghostRunning = findRunning(command.ghostRunningId());
            ghostRunning.verifyCourseId(courseId);
        }
    }

    private ProcessedTelemetriesDto processTelemetry(CreateRunCommand command) {
        return TelemetryCalculator.processTelemetry(command.telemetries(), command.startedAt());
    }

    private String uploadTelemetryToS3(String memberUuid, ProcessedTelemetriesDto processedTelemetry) {
        String stringTelemetries = TelemetryTypeConverter.convertFromObjectsToString(processedTelemetry.getRelativeTelemetries());
        return s3TelemetryClient.uploadTelemetries(stringTelemetries, memberUuid);
    }

    private RunningRecord createRunningRecord(RunRecordDto command, ProcessedTelemetriesDto processedTelemetry) {
        return RunningRecord.of(command.distance(), command.elevationGain(), command.elevationLoss(), command.avgPace(), processedTelemetry.getHighestPace(),
                processedTelemetry.getLowestPace(), command.duration(), command.calories(), command.avgCadence(), command.avgBpm());
    }

    private Running createAndSaveRunning(CreateRunCommand command, ProcessedTelemetriesDto processedTelemetry, String url, Member member, Course course) {
        RunningRecord runningRecord = createRunningRecord(command.record(), processedTelemetry);
        return runningRepository.save(Running.of(command.runningName(), RunningMode.valueOf(command.mode()),
                command.ghostRunningId(), runningRecord, command.startedAt(), command.isPublic(), command.hasPaused(), url, member, course));
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

    private Course createAndSaveCourse(
            Member member, CreateRunCommand command, ProcessedTelemetriesDto processedTelemetry) {
        Course course = Course.of(member, CourseProfile.of(
                command.record().distance(), command.record().elevationGain(),
                command.record().elevationLoss()), processedTelemetry.getStartPoint(),
                processedTelemetry.getCourseCoordinates(), null);
        courseService.save(course);
        return course;
    }

    @Transactional
    public void deleteRunnings(List<Long> runningIds, String memberUuid) {
        List<Running> runnings = runningRepository.findByIds(runningIds);
        runnings.forEach(running -> running.verifyMember(memberUuid));
        runningRepository.deleteAllByIdIn(runningIds);
    }

}
