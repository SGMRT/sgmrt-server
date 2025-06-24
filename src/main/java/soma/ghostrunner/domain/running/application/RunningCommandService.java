package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.S3Uploader;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.TelemetryParser;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.CourseService;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.CreateRunningCommand;
import soma.ghostrunner.domain.running.application.dto.RunRecordCommand;
import soma.ghostrunner.domain.running.application.dto.TelemetryCommand;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.common.error.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final S3Uploader s3Uploader;
    private final RunningRepository runningRepository;

    private final CourseService courseService;
    private final MemberService memberService;

    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(CreateRunningCommand command, String courseName, Long memberId) {

        Member member = memberService.findMemberById(memberId);

        // 상대 시간으로 변경 및 S3 업로드
        String url = s3Uploader.uploadTelemetry(convertTelemetriesToRelativeTimestamp(command), memberId);

        // 코스, 러닝 생성 및 저장
        Course course = createAndSaveCourse(command, courseName);
        RunningRecord runningRecord = createRunningRecord(command.record());
        Running running = createAndSaveRunning(command, runningRecord, url, member, course);

        return CreateCourseAndRunResponse.of(running.getId(), course.getId());
    }

    @Transactional
    public Long createRun(CreateRunningCommand command, Long courseId, Long memberId) {

        Member member = memberService.findMemberById(memberId);

        // 상대 시간으로 변경 및 S3 업로드
        String url = s3Uploader.uploadTelemetry(convertTelemetriesToRelativeTimestamp(command), memberId);

        // 코스 및 고스트가 뛴 기록 검증
        Course course = courseService.findCourseById(courseId);
        verifyGhostRunningExist(command.ghostRunningId(), courseId);

        // 러닝 생성 및 저장
        RunningRecord runningRecord = createRunningRecord(command.record());
        Running running = createAndSaveRunning(command, runningRecord, url, member, course);

        return running.getId();
    }

    private List<TelemetryCommand> convertTelemetriesToRelativeTimestamp(CreateRunningCommand command) {
        return TelemetryParser.convertAbsoluteToRelativeTimestamp(command.telemetries(), command.startedAt());
    }

    private void verifyGhostRunningExist(Long ghostRunningId, Long courseId) {
        if (ghostRunningId != null) {
            List<Long> runningIds = runningRepository.findIdsByCourseId(courseId);
            if (!runningIds.contains(ghostRunningId)) {
                throw new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, ghostRunningId);
            }
        }
    }

    private Running createAndSaveRunning(CreateRunningCommand command, RunningRecord runningRecord, String url, Member member, Course course) {
        return runningRepository.save(Running.of(RunningMode.valueOf(command.mode()), command.ghostRunningId(), runningRecord,
                command.startedAt(), command.isPublic(), command.hasPaused(), url, member, course));
    }

    private RunningRecord createRunningRecord(RunRecordCommand command) {
        return RunningRecord.of(
                command.distance(), command.altitude(), command.avgPace(),
                command.duration(), command.calories(), command.avgCadence(), command.avgBpm());
    }

    private Course createAndSaveCourse(CreateRunningCommand command, String courseName) {
        Course course = Course.of(courseName,
                CourseMetaInfo.of(command.record().distance(), command.record().altitude()),
                TelemetryParser.extractStartPoint(command.telemetries()),
                TelemetryParser.extractCourseCoordinates(command.telemetries()));
        courseService.save(course);
        return course;
    }
}
