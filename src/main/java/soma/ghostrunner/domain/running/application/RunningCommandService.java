package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.events.CourseMapDataChangedEvent;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        courseReadModelWriter.applyRun(running);
        memberVdotWriter.updateFromRun(member.getUuid(), running.getRunningRecord().getAveragePace());
        // 소비자(AFTER_COMMIT): 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) + 구경로 코스 캐시 무효화(CourseCacheEventListener)
        // 구경로 정리 시 후자의 리스너만 지우고 이 발행 자체는 남긴다 — 자세한 이유는 publishCourseRunEvents javadoc 참고
        eventPublisher.publishEvent(running.createFinishedEvent());
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

        courseReadModelWriter.applyRun(running);
        memberVdotWriter.updateFromRun(member.getUuid(), running.getRunningRecord().getAveragePace());
        courseSubscriptionService.subscribeIfAbsent(courseId, member.getId());
        publishCourseRunEvents(running);
        return running.getId();
    }

    /**
     * 코스를 따라 뛴 러닝의 종료 이벤트를 발행한다. (소비자는 전부 AFTER_COMMIT 부수효과)
     *
     * <pre>
     * - RunFinishedEvent → (1) 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) — <b>존치</b>.
     *                          "완주 직후 지도에서 내 등수를 본다"(설계 course-cell-bucket-cache-design §1-2)가
     *                          이 발행에 걸려 있다.
     *                      (2) 구경로 코스 캐시 무효화(CourseCacheEventListener) — 구경로 제거 시 함께 삭제
     * - CourseRunEvent   → 푸시 발송(PushEventListener)
     * </pre>
     *
     * <p>구경로 정리(설계 결정 10) 시 (2)의 리스너만 지우고 <b>이 발행 자체는 남겨야 한다.</b>
     * 발행을 지우면 컴파일도 테스트도 통과하지만 지도 이빅트가 사라져 완주 반영이 최대 TTL(600초)까지 지연된다.
     * ({@code RunningCommandServiceTest}가 이 발행을 계약으로 고정하고 있다.)</p>
     *
     * <p>VDOT 갱신·구독 생성은 같은 트랜잭션 동기 로직이라 직접 호출로 전환됨 (설계 04 §6).</p>
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
        // RunUpdatedEvent 소비자(AFTER_COMMIT): 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) — 존치
        //                                    + 구경로 코스 캐시 무효화(CourseCacheEventListener) — 구경로 제거 시 함께 삭제
        eventPublisher.publishEvent(running.createUpdatedEvent());
    }

    @Transactional
    public void updateRunningPublicStatus(Long runningId, String memberUuid) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        running.updatePublicStatus();
        // RunUpdatedEvent 소비자(AFTER_COMMIT): 지도 셀 캐시 이빅트(CourseCellCacheEvictListener) — 존치
        //                                    + 구경로 코스 캐시 무효화(CourseCacheEventListener) — 구경로 제거 시 함께 삭제
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

    /**
     * 러닝들을 삭제하고 영향받은 코스를 동기화한다. 수집 → 삭제 → 재계산 → 발행 순서로 진행한다.
     *
     * <p>수집이 반드시 삭제보다 앞서야 한다. {@code deleteInRunningIds}는
     * {@code @Modifying(clearAutomatically = true)}라 벌크 삭제 직후 영속성 컨텍스트가 비워지고,
     * LAZY인 {@code Running.course} 프록시는 미초기화 상태로 detach 된다.
     * 그 뒤에 좌표를 읽으면 {@code LazyInitializationException}이 나 러닝 삭제 API가 항상 500이 된다.
     * (설계: docs/design/course-cell-bucket-cache-design.md §4 경로 e · [R2] · D5)
     */
    @Transactional
    public void deleteRunnings(List<Long> runningIds, String memberUuid) {
        List<Running> runningsToDelete = runningRepository.findByIds(runningIds);
        runningsToDelete.forEach(running -> running.verifyMember(memberUuid));

        // 1. 수집 — 삭제하면 알아낼 수 없는 정보(재계산 대상 코스, 지도 이빅트용 시작점 좌표)를 미리 확보한다
        List<Course> affectedCourses = distinctCoursesOf(runningsToDelete);
        List<Long> affectedCourseIds = affectedCourses.stream()
                .map(Course::getId)
                .toList();
        List<CourseMapDataChangedEvent> mapDataChanges = affectedCourses.stream()
                .map(Course::createMapDataChangedEvent)  // LAZY 프록시가 초기화되는 지점 — 아직 영속성 컨텍스트가 살아있어야 한다
                .toList();

        // 2. 삭제
        runningRepository.deleteInRunningIds(runningIds);

        // 3. 재계산 — 남은 러닝만으로 코스 집계를 다시 계산한다
        courseReadModelWriter.recalculate(affectedCourseIds);

        // 4. 발행 — 리드모델 최종 상태가 확정된 뒤 발행한다 (구독자는 AFTER_COMMIT 지도 캐시 이빅트)
        mapDataChanges.forEach(eventPublisher::publishEvent);
    }

    /**
     * 러닝들이 속한 코스를 중복 없이 모은다. 같은 코스의 러닝이 여러 건이어도 코스는 1건이다.
     * 어느 코스에도 속하지 않은 러닝은 재계산·이빅트 대상이 아니므로 제외한다.
     */
    private List<Course> distinctCoursesOf(List<Running> runnings) {
        Map<Long, Course> coursesById = new LinkedHashMap<>();
        for (Running running : runnings) {
            Course course = running.getCourse();
            if (course == null) {
                continue;
            }
            coursesById.putIfAbsent(course.getId(), course);  // 식별자 게터라 프록시를 초기화하지 않는다
        }
        return List.copyOf(coursesById.values());
    }

}
