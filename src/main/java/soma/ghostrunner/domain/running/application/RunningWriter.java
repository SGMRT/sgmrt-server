package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.application.CourseMapCacheEvictor;
import soma.ghostrunner.domain.course.application.CourseReader;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseSubscriptionWriter;
import soma.ghostrunner.domain.course.application.CourseWriter;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 러닝 도메인의 <b>DB 쓰기 트랜잭션 경계</b>. 러닝의 생성·수정·삭제 트랜잭션은 전부 여기서 연다.
 * (구 {@code RunningCreationWriter} — 수정·삭제 계열을 {@code RunningCommandService}에서 흡수하며 개명)
 *
 * <p><b>왜 별도 빈인가</b> — {@code RunningCommandService} 안의 private 메서드에 {@code @Transactional}을 붙이면
 * self-invocation 이라 프록시를 타지 않아 <b>트랜잭션이 아예 걸리지 않는다</b>(조용히 실패하는 형태라 더 나쁘다).
 * 이 저장소는 이미 같은 이유로 경계를 별도 빈에 두는 패턴을 쓴다 — {@code SqsWorkerInternalService},
 * {@code MemberVdotWriter}, {@code CourseWriter}.
 *
 * <p><b>왜 경계를 여기까지만 좁혔나</b> — 시계열 가공(CPU)과 S3 업로드(네트워크 I/O 5회/3회)가 예전에는 같은
 * 트랜잭션 안에 있었다. DB 커넥션을 쥔 채 외부 I/O를 하면 동시 요청이 늘 때 커넥션 풀이 먼저 마른다.
 * 그래서 가공·업로드는 호출자가 트랜잭션 <b>밖</b>에서 끝낸 뒤 결과값(URL·통계)만 넘긴다.
 *
 * <p><b>이 안에 반드시 남아야 하는 것들</b>
 * <ul>
 *   <li>{@link CourseReadModelWriter#applyRun}·{@code recalculate} — {@code Propagation.MANDATORY}.
 *       밖으로 나가면 즉시 예외다.</li>
 *   <li>{@link CourseMapCacheEvictor} 이빅트 예약 — 커밋 후 실행을 트랜잭션 동기화로 예약한다.
 *       활성 트랜잭션이 없으면 등록 자체가 불가능하다.</li>
 *   <li>{@code CourseRunEvent} 발행 — 소비자({@code PushEventListener})가
 *       {@code @TransactionalEventListener(AFTER_COMMIT)}라, 트랜잭션 밖에서 발행하면 이벤트가
 *       <b>조용히 버려져 푸시 알림이 죽는다.</b></li>
 *   <li>{@code CourseSubscriptionWriter#subscribeIfAbsent} — 자체 트랜잭션이 없어 호출자 트랜잭션에 의존한다.</li>
 * </ul>
 *
 * <p><b>밖으로 뺀 것</b> — VDOT 갱신은 자체 트랜잭션으로 분리했다. VDOT 실패가 러닝 저장을 롤백시켜
 * "방금 뛴 기록이 통째로 사라지는" 것보다, VDOT만 낡은 채 두고 개발자가 인지하는 편이 낫다.
 */
@Service
@RequiredArgsConstructor
public class RunningWriter {

    private final RunningApplicationMapper mapper;
    private final RunningRepository runningRepository;
    private final RunningReader runningReader;
    private final CourseWriter courseWriter;
    private final CourseReader courseReader;
    private final CourseReadModelWriter courseReadModelWriter;
    private final CourseSubscriptionWriter courseSubscriptionWriter;
    private final CourseMapCacheEvictor courseMapCacheEvictor;
    private final ApplicationEventPublisher eventPublisher;

    /** 일반 러닝 — 코스와 러닝을 함께 만든다. */
    @Transactional
    public CreatedRun saveRunAndCourse(CreateRunCommand command, Member member,
                                       TelemetryStatistics telemetryStatistics, RunningDataUrlsDto dataUrls) {
        Course course = mapper.toCourse(member, command, telemetryStatistics, dataUrls);
        courseWriter.save(course);

        Running running = runningRepository.save(
                mapper.toRunning(command, telemetryStatistics, dataUrls, member, course));

        courseReadModelWriter.applyRun(running);
        courseMapCacheEvictor.evictCourseCellAfterCommit(course.getId());
        return new CreatedRun(running, course);
    }

    /**
     * 코스를 따라 뛴 러닝 — 기존 코스에 러닝만 붙인다.
     *
     * <p>코스는 <b>이 트랜잭션 안에서 다시 조회한다.</b> 호출자가 트랜잭션 밖에서 읽은 {@code Course}를 그대로
     * 넘기면 detached 라서, 커밋 시점의 LAZY 접근({@code course.getMember()} 등)이 터진다.</p>
     */
    @Transactional
    public Running saveRun(CreateRunCommand command, Member member, Long courseId,
                           TelemetryStatistics telemetryStatistics, RunningDataUrlsDto dataUrls) {

        Course course = courseReader.findCourseByIdFetchJoinMember(courseId);

        Running running = runningRepository.save(
                mapper.toRunning(command, telemetryStatistics, dataUrls, member, course));

        courseReadModelWriter.applyRun(running);
        courseSubscriptionWriter.subscribeIfAbsent(courseId, member.getId());
        // "완주 직후 지도에서 내 등수를 본다"(설계 §1-2) — 커밋 후 셀 하나를 지우도록 예약한다
        courseMapCacheEvictor.evictCourseCellAfterCommit(courseId);
        publishCourseRunEvent(running);
        return running;
    }

    @Transactional
    public void updateName(String name, Long runningId, String memberUuid) {
        Running running = findRunning(runningId);
        running.verifyMember(memberUuid);
        running.updateName(name);
        // 러닝 이름은 지도 카드에 노출되므로 셀 캐시도 커밋 후 지워야 한다
        courseMapCacheEvictor.evictCourseCellAfterCommit(courseIdOf(running));
    }

    @Transactional
    public void updatePublicStatus(Long runningId, String memberUuid) {
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
                .map(RunningWriter::mapCellOf)  // LAZY 프록시가 초기화되는 지점 — 아직 영속성 컨텍스트가 살아있어야 한다
                .toList();

        // 2. 삭제
        runningRepository.deleteInRunningIds(runningIds);

        // 3. 재계산 — 남은 러닝만으로 코스 집계를 다시 계산한다
        courseReadModelWriter.recalculate(affectedCourseIds);

        // 4. 이빅트 예약 — 리드모델 최종 상태가 확정된 뒤 예약한다 (실행은 커밋 후)
        mapCells.forEach(cell ->
                courseMapCacheEvictor.evictCellAfterCommit(cell.courseId(), cell.startLat(), cell.startLng()));
    }

    private Running findRunning(Long runningId) {
        return runningReader.findRunningByRunningId(runningId);
    }

    /** 어느 코스에도 속하지 않은 러닝은 지울 셀이 없다. (식별자 게터라 프록시를 초기화하지 않는다) */
    private Long courseIdOf(Running running) {
        Course course = running.getCourse();
        return course != null ? course.getId() : null;
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

    /**
     * 코스를 따라 뛴 러닝의 완주 이벤트를 발행한다. 소비자는 푸시 발송({@code PushEventListener}) 하나이며,
     * AFTER_COMMIT 부수효과다. 그래서 <b>반드시 이 트랜잭션 안에서</b> 발행돼야 한다.
     *
     * <p>지도 셀 캐시 이빅트는 이 발행에 걸려 있지 않다 — {@link CourseMapCacheEvictor} 직접 호출이 담당한다.
     * VDOT 갱신·구독 생성도 이벤트가 아닌 직접 호출로 전환됐다 (설계 04 §6).</p>
     */
    private void publishCourseRunEvent(Running running) {
        eventPublisher.publishEvent(running.createCourseRunEvent());
    }

    /**
     * 저장 트랜잭션의 결과. 커밋 이후 호출자는 식별자만 읽으므로 detached 여도 안전하다.
     */
    public record CreatedRun(Running running, Course course) {
    }
}
