package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.S3Uploader;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetryResult;
import soma.ghostrunner.domain.running.domain.TelemetryProcessor;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.CourseService;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.CreateRunningCommand;
import soma.ghostrunner.domain.running.application.dto.RunRecordCommand;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final S3Uploader s3Uploader;
    private final RunningRepository runningRepository;

    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;

    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(CreateRunningCommand command, Long memberId) {

        Member member = memberService.findMemberById(memberId);

        // 시계열 가공 및 S3 업로드
        ProcessedTelemetryResult processedTelemetry = processTelemetry(command);
        String url = s3Uploader.uploadTelemetry(processedTelemetry.getRelativeTelemetries(), memberId);

        // 코스, 러닝 생성 및 저장
        Course course = createAndSaveCourse(command, processedTelemetry);
        RunningRecord runningRecord = createRunningRecord(command.record(), processedTelemetry);
        Running running = createAndSaveRunning(command, runningRecord, url, member, course);

        return CreateCourseAndRunResponse.of(running.getId(), course.getId());
    }

    @Transactional
    public Long createRun(CreateRunningCommand command, Long courseId, Long memberId) {

        Member member = memberService.findMemberById(memberId);

        // 상대 시간으로 변경 및 S3 업로드
        ProcessedTelemetryResult processedTelemetry = processTelemetry(command);
        String url = s3Uploader.uploadTelemetry(processedTelemetry.getRelativeTelemetries(), memberId);

        // 코스 및 고스트가 뛴 기록 검증
        Course course = courseService.findCourseById(courseId);
        runningQueryService.findRunningById(command.ghostRunningId());

        // 러닝 생성 및 저장
        RunningRecord runningRecord = createRunningRecord(command.record(), processedTelemetry);
        Running running = createAndSaveRunning(command, runningRecord, url, member, course);

        return running.getId();
    }

    @Transactional
    public void updateRunningName(String name, Long memberId, Long runningId) {
        Running running = runningQueryService.findByRunningIdAndMemberId(runningId, memberId);
        running.updateName(name);
    }

    private ProcessedTelemetryResult processTelemetry(CreateRunningCommand command) {
        return TelemetryProcessor.processTelemetry(command.telemetries(), command.startedAt());
    }

    private Running createAndSaveRunning(CreateRunningCommand command, RunningRecord runningRecord, String url, Member member, Course course) {
        return runningRepository.save(Running.of(command.runningName(), RunningMode.valueOf(command.mode()),
                command.ghostRunningId(), runningRecord, command.startedAt(), command.isPublic(), command.hasPaused(), url, member, course));
    }

    private RunningRecord createRunningRecord(RunRecordCommand command, ProcessedTelemetryResult processedTelemetry) {
        return RunningRecord.of(command.distance(), command.elevationGain(), command.elevationLoss(), command.avgPace(), processedTelemetry.getHighestPace(),
                processedTelemetry.getLowestPace(), command.duration(), command.calories(), command.avgCadence(), command.avgBpm());
    }

    private Course createAndSaveCourse(CreateRunningCommand command, ProcessedTelemetryResult processedTelemetry) {
        Course course = Course.of(CourseMetaInfo.of(command.record().distance(), command.record().elevationGain(),
                        command.record().elevationLoss()), processedTelemetry.getStartPoint(), processedTelemetry.getCourseCoordinates());
        courseService.save(course);
        return course;
    }
}
