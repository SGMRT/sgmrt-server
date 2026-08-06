package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.application.CourseMapCacheEvictor;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.RunningCreationWriter.CreatedRun;
import soma.ghostrunner.domain.running.application.dto.*;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.application.MemberVdotWriter;
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

/**
 * 러닝 쓰기 유즈케이스의 조율자.
 *
 * <p><b>러닝 생성은 트랜잭션을 열지 않는다.</b> 시계열 가공(CPU)과 S3 업로드(네트워크 I/O)를 DB 커넥션을 쥔 채
 * 수행하면 동시 요청이 늘 때 커넥션 풀이 먼저 마르기 때문이다. 순서는 "조회·가공·업로드 → 저장 트랜잭션 → VDOT"이고,
 * DB 쓰기 경계는 {@link RunningCreationWriter}가 단독으로 갖는다.
 *
 * <p>수정·삭제 계열({@code updateRunningName}, {@code updateRunningPublicStatus}, {@code deleteRunnings})은
 * 무거운 외부 I/O가 없으므로 기존대로 이 클래스가 트랜잭션을 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningCommandService {

    private final RunningApplicationMapper mapper;

    private final RunningRepository runningRepository;

    private final TelemetryProcessor telemetryProcessor;
    private final RunningFileUploader runningFileUploader;

    private final PathSimplificationService pathSimplificationService;
    private final RunningQueryService runningQueryService;
    private final CourseService courseService;
    private final MemberService memberService;
    private final CourseReadModelWriter courseReadModelWriter;
    private final MemberVdotWriter memberVdotWriter;
    private final CourseMapCacheEvictor courseMapCacheEvictor;
    private final RunningCreationWriter runningCreationWriter;

    public CreateCourseAndRunResponse createRunAndCourse(
            CreateRunCommand command, String memberUuid, MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);

        // TODO: 러닝 데이터 가공해서 S3에 업로드하는 것은 비동기로 빼는 것 고민하기
        TelemetryStatistics telemetryStatistics = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        SimplifiedPaths simplifiedPaths = pathSimplificationService.simplify(telemetryStatistics);
        RunningDataUrlsDto dataUrlsDto = upload(rawTelemetry, telemetryStatistics, simplifiedPaths, screenShotImage, member);

        CreatedRun created = runningCreationWriter.saveRunAndCourse(command, member, telemetryStatistics, dataUrlsDto);
        updateVdotOrAlert(memberUuid, created.running());
        return mapper.toResponse(created.running(), created.course());
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

    /**
     * VDOT를 갱신하되, 실패해도 이미 커밋된 러닝을 되돌리지 않는다.
     *
     * <p>VDOT 원자성을 포기한 이유는 손해의 비대칭이다 — 사용자가 방금 뛴 기록이 통째로 사라지는 것보다,
     * VDOT만 낡은 채 두고(다음 러닝에서 재계산된다) 개발자가 인지하는 편이 낫다.
     *
     * <p>별도의 알림 채널을 두지 않는 이유는 {@code logback-spring.xml}에 있다 — {@code SentryAppender}가
     * ERROR {@code ThresholdFilter}와 함께 prod 프로파일 root 에 물려 있어, 운영에서 {@code log.error}는
     * 그 자체로 Sentry 알럿이 된다. (warn 이 아니라 error 인 이유 — 이 저장소 관례상 warn 은
     * "TTL 안에 자가 치유되는 것", error 는 "데이터가 실제로 어긋난 채 남아 사람이 봐야 하는 것"이다.)
     */
    private void updateVdotOrAlert(String memberUuid, Running running) {
        try {
            memberVdotWriter.updateFromRun(memberUuid, running.getRunningRecord().getAveragePace());
        } catch (Exception e) {
            log.error("VDOT 갱신 실패, 러닝은 정상 저장됨 - memberUuid={}, runningId={}", memberUuid, running.getId(), e);
        }
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

    /**
     * 코스를 따라 뛴 러닝을 저장한다.
     *
     * <p>코스 조회와 GHOST 검증은 읽기라 트랜잭션 밖에 둔다 — 존재하지 않는 코스/다른 코스의 고스트라면
     * S3 업로드를 시작하기 전에 실패하는 편이 낫다(fail-fast). 저장에 쓸 코스는
     * detached 를 피하려고 {@code courseId}만 넘겨 트랜잭션 안에서 다시 조회한다.
     */
    public Long createRun(CreateRunCommand command, String memberUuid, Long courseId,
                          MultipartFile rawTelemetry, MultipartFile interpolatedTelemetry, MultipartFile screenShotImage) {

        Member member = findMember(memberUuid);
        findCourse(courseId);  // fail-fast — 없는 코스면 업로드 전에 끝낸다
        validateBelongsToCourseIfGhostMode(command, courseId);

        TelemetryStatistics processedTelemetries = telemetryProcessor.process(interpolatedTelemetry, command.getStartedAt());
        RunningDataUrlsDto runningDataUrlsDto = upload(rawTelemetry, processedTelemetries, screenShotImage, member);

        Running running = runningCreationWriter.saveRun(command, member, courseId, processedTelemetries, runningDataUrlsDto);

        updateVdotOrAlert(memberUuid, running);
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
        // 지도 셀 캐시 이빅트는 직접 호출로 예약한다 (커밋 후 실행)
        courseMapCacheEvictor.evictCourseCellAfterCommit(courseIdOf(running));
    }

    @Transactional
    public void updateRunningPublicStatus(Long runningId, String memberUuid) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        running.updatePublicStatus();
        // 지도 셀 캐시 이빅트는 직접 호출로 예약한다 (커밋 후 실행)
        courseMapCacheEvictor.evictCourseCellAfterCommit(courseIdOf(running));

        // 공개 여부가 바뀌면 집계 모집단이 달라지므로 해당 코스의 리드모델을 다시 계산한다
        Course course = running.getCourse();
        if (course != null) {
            courseReadModelWriter.recalculate(List.of(course.getId()));
        }
    }

    private Running findRunning(Long runningId) {
        return runningQueryService.findRunningByRunningId(runningId);
    }

    /** 어느 코스에도 속하지 않은 러닝은 지울 셀이 없다. (식별자 게터라 프록시를 초기화하지 않는다) */
    private Long courseIdOf(Running running) {
        Course course = running.getCourse();
        return course != null ? course.getId() : null;
    }

    /**
     * 러닝들을 삭제하고 영향받은 코스를 동기화한다. 수집 → 삭제 → 재계산 → 이빅트 예약 순서로 진행한다.
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
        List<CourseMapCell> mapCells = affectedCourses.stream()
                .map(RunningCommandService::mapCellOf)  // LAZY 프록시가 초기화되는 지점 — 아직 영속성 컨텍스트가 살아있어야 한다
                .toList();

        // 2. 삭제
        runningRepository.deleteInRunningIds(runningIds);

        // 3. 재계산 — 남은 러닝만으로 코스 집계를 다시 계산한다
        courseReadModelWriter.recalculate(affectedCourseIds);

        // 4. 이빅트 예약 — 리드모델 최종 상태가 확정된 뒤 예약한다 (실행은 커밋 후)
        mapCells.forEach(cell ->
                courseMapCacheEvictor.evictCellAfterCommit(cell.courseId(), cell.startLat(), cell.startLng()));
    }

    /** 코스의 시작점 좌표를 값으로 뽑아 둔다. 벌크 삭제 이후에는 이 초기화가 불가능하다([R2]). */
    private static CourseMapCell mapCellOf(Course course) {
        Coordinate startCoordinate = course.getStartCoordinate();
        return new CourseMapCell(
                course.getId(),
                startCoordinate != null ? startCoordinate.getLatitude() : null,
                startCoordinate != null ? startCoordinate.getLongitude() : null);
    }

    /** 이빅트에 필요한 값만 담은 스냅샷. 엔티티를 커밋 후까지 들고 가지 않기 위한 것이다([R2]). */
    private record CourseMapCell(Long courseId, Double startLat, Double startLng) {
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
