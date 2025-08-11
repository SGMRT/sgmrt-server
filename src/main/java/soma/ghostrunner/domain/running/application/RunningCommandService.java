package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.upload.S3TelemetryClient;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.RunningServiceMapper;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.application.support.TelemetryTypeConverter;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final S3TelemetryClient s3TelemetryClient;
    private final RunningServiceMapper mapper;

    private final RunningRepository runningRepository;

    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;

    /**
     * 1. 시계열 처리 : 검증 + 틍계 ( 상대시간, 최고, 최저 속도 ) + 위경도 좌표 추출
     * 2. 위경도 좌표 RDP 알고리즘
     * 3. S3 저장
     * 4. DB 저장 + 이벤트 발행
     */
    @Transactional
    public CreateCourseAndRunResponse createCourseAndRun(CreateRunCommand command, String memberUuid) {

        Member member = memberService.findMemberByUuid(memberUuid);

        ProcessedTelemetriesDto processedTelemetry = processTelemetry(command);

        String telemetryUrl = uploadTelemetryToS3(memberUuid, processedTelemetry);
        Course course = createAndSaveCourse(member, command, processedTelemetry.startPoint(), null);
        Running running = createAndSaveRunning(command, processedTelemetry, telemetryUrl, null, null, member, course);

        return CreateCourseAndRunResponse.of(running.getId(), course.getId());
    }

    /**
     * 1. 시계열 처리 : 검증 + 틍계 ( 상대시간, 최고, 최저 속도 )
     * 2. S3 저장
     * 3. DB 저장 + 이벤트 발행
     */
    @Transactional
    public Long createRun(CreateRunCommand command, Long courseId, String memberUuid) {
        Member member = memberService.findMemberByUuid(memberUuid);
        verifyCourseIdIfGhostMode(command, courseId);

        ProcessedTelemetriesDto processedTelemetry = processTelemetry(command);
        String url = uploadTelemetryToS3(memberUuid, processedTelemetry);

        Course course = courseService.findCourseById(courseId);
        Running running = createAndSaveRunning(command, processedTelemetry, url, null, null, member, course);
        return running.getId();
    }

    private void verifyCourseIdIfGhostMode(CreateRunCommand command, Long courseId) {
        if (command.mode().equals("GHOST")) {
            Running ghostRunning = findRunning(command.ghostRunningId());
            ghostRunning.verifyCourseId(courseId);
        }
    }

    private ProcessedTelemetriesDto processTelemetry(CreateRunCommand command) {
        return null;
//        return TelemetryCalculator.processTelemetry(null, command.startedAt());
    }

    private String uploadTelemetryToS3(String memberUuid, ProcessedTelemetriesDto processedTelemetry) {
        String stringTelemetries = TelemetryTypeConverter.convertFromObjectsToString(processedTelemetry.relativeTelemetries());
        return null;
    }

    private Running createAndSaveRunning(CreateRunCommand command, ProcessedTelemetriesDto processedTelemetry,
                                         String rawTelemetrySavedUrl, String interpolatedTelemetrySavedUrl,
                                         String screenShotSavedUrl, Member member, Course course) {
        return runningRepository.save(mapper.toRunning(command, processedTelemetry, rawTelemetrySavedUrl,
                interpolatedTelemetrySavedUrl, screenShotSavedUrl, member, course));
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

    private Course createAndSaveCourse(Member member, CreateRunCommand command,
                                       CoordinateDto startCoordinateDto, String pathDataSavedUrl) {
        Course course = mapper.toCourse(member, command, startCoordinateDto, pathDataSavedUrl);
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
