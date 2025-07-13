package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.TelemetryClient;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordDto;
import soma.ghostrunner.domain.running.domain.support.TelemetryCalculator;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.domain.support.TelemetryTypeConverter;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final TelemetryClient telemetryClient;
    private final RunningRepository runningRepository;

    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;

    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(CreateRunCommand command, Long memberId) {

        Member member = memberService.findMemberById(memberId);

        ProcessedTelemetriesDto processedTelemetry = processTelemetry(command);
        String url = uploadTelemetryToS3(memberId, processedTelemetry);

        Course course = createAndSaveCourse(command, processedTelemetry);
        Running running = createAndSaveRunning(command, processedTelemetry, url, member, course);

        return CreateCourseAndRunResponse.of(running.getId(), course.getId());
    }

    @Transactional
    public Long createRun(CreateRunCommand command, Long courseId, Long memberId) {

        Member member = memberService.findMemberById(memberId);
        verifyCourseIdIfGhostMode(command, courseId);

        ProcessedTelemetriesDto processedTelemetry = processTelemetry(command);
        String url = uploadTelemetryToS3(memberId, processedTelemetry);

        Course course = courseService.findCourseById(courseId);
        Running running = createAndSaveRunning(command, processedTelemetry, url, member, course);
        return running.getId();
    }

    private void verifyCourseIdIfGhostMode(CreateRunCommand command, Long courseId) {
        if (command.mode().equals("GHOST")) {
            Running ghostRunning = findRunningBy(command.ghostRunningId());
            ghostRunning.verifyCourseId(courseId);
        }
    }

    private ProcessedTelemetriesDto processTelemetry(CreateRunCommand command) {
        return TelemetryCalculator.processTelemetry(command.telemetries(), command.startedAt());
    }

    private String uploadTelemetryToS3(Long memberId, ProcessedTelemetriesDto processedTelemetry) {
        String stringTelemetries = TelemetryTypeConverter.convertFromObjectsToString(processedTelemetry.getRelativeTelemetries());
        return telemetryClient.uploadTelemetries(stringTelemetries, memberId);
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
    public void updateRunningName(String name, Long memberId, Long runningId) {
        Running running = findRunningBy(memberId, runningId);
        running.updateName(name);
    }

    @Transactional
    public void updateRunningPublicStatus(Long runningId) {
        Running running = findRunningBy(runningId);
        running.updatePublicStatus();
    }

    private Running findRunningBy(Long runningId) {
        return runningQueryService.findRunningByRunningId(runningId);
    }

    private Running findRunningBy(Long memberId, Long runningId) {
        return runningQueryService.findRunningByRunningId(runningId, memberId);

    }

    private Course createAndSaveCourse(CreateRunCommand command, ProcessedTelemetriesDto processedTelemetry) {
        Course course = Course.of(CourseProfile.of(command.record().distance(), command.record().elevationGain(),
                        command.record().elevationLoss()), processedTelemetry.getStartPoint(), processedTelemetry.getCourseCoordinates());
        courseService.save(course);
        return course;
    }

}
