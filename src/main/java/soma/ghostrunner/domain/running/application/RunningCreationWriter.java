package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.application.CourseMapCacheEvictor;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.application.CourseSubscriptionService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

/**
 * 러닝 생성의 <b>DB 쓰기 트랜잭션 경계</b>. 코스·러닝·리드모델 저장만 담는다.
 *
 * <p><b>왜 별도 빈인가</b> — {@code RunningCommandService} 안의 private 메서드에 {@code @Transactional}을 붙이면
 * self-invocation 이라 프록시를 타지 않아 <b>트랜잭션이 아예 걸리지 않는다</b>(조용히 실패하는 형태라 더 나쁘다).
 * 이 저장소는 이미 같은 이유로 경계를 별도 빈에 두는 패턴을 쓴다 —
 * {@code PacemakerRecoveryPrepareService}("Self-Invocation 문제 방지를 위해 분리"), {@code SqsWorkerInternalService}.
 *
 * <p><b>왜 경계를 여기까지만 좁혔나</b> — 시계열 가공(CPU)과 S3 업로드(네트워크 I/O 5회/3회)가 예전에는 같은
 * 트랜잭션 안에 있었다. DB 커넥션을 쥔 채 외부 I/O를 하면 동시 요청이 늘 때 커넥션 풀이 먼저 마른다.
 * 그래서 가공·업로드는 호출자가 트랜잭션 <b>밖</b>에서 끝낸 뒤 결과값(URL·통계)만 넘긴다.
 *
 * <p><b>이 안에 반드시 남아야 하는 것들</b>
 * <ul>
 *   <li>{@link CourseReadModelWriter#applyRun} — {@code Propagation.MANDATORY}. 밖으로 나가면 즉시 예외다.</li>
 *   <li>{@link CourseMapCacheEvictor} 이빅트 예약 — 커밋 후 실행을 트랜잭션 동기화로 예약한다.
 *       활성 트랜잭션이 없으면 등록 자체가 불가능하다.</li>
 *   <li>{@code CourseRunEvent} 발행 — 소비자({@code PushEventListener})가
 *       {@code @TransactionalEventListener(AFTER_COMMIT)}라, 트랜잭션 밖에서 발행하면 이벤트가
 *       <b>조용히 버려져 푸시 알림이 죽는다.</b></li>
 *   <li>{@code CourseSubscriptionService#subscribeIfAbsent} — 자체 트랜잭션이 없어 호출자 트랜잭션에 의존한다.</li>
 * </ul>
 *
 * <p><b>밖으로 뺀 것</b> — VDOT 갱신은 자체 트랜잭션으로 분리했다. VDOT 실패가 러닝 저장을 롤백시켜
 * "방금 뛴 기록이 통째로 사라지는" 것보다, VDOT만 낡은 채 두고 개발자가 인지하는 편이 낫다.
 */
@Service
@RequiredArgsConstructor
public class RunningCreationWriter {

    private final RunningApplicationMapper mapper;
    private final RunningRepository runningRepository;
    private final CourseService courseService;
    private final CourseReadModelWriter courseReadModelWriter;
    private final CourseSubscriptionService courseSubscriptionService;
    private final CourseMapCacheEvictor courseMapCacheEvictor;
    private final ApplicationEventPublisher eventPublisher;

    /** 일반 러닝 — 코스와 러닝을 함께 만든다. */
    @Transactional
    public CreatedRun saveRunAndCourse(CreateRunCommand command, Member member,
                                       TelemetryStatistics telemetryStatistics, RunningDataUrlsDto dataUrls) {
        Course course = mapper.toCourse(member, command, telemetryStatistics, dataUrls);
        courseService.save(course);

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

        Course course = courseService.findCourseByIdFetchJoinMember(courseId);

        Running running = runningRepository.save(
                mapper.toRunning(command, telemetryStatistics, dataUrls, member, course));

        courseReadModelWriter.applyRun(running);
        courseSubscriptionService.subscribeIfAbsent(courseId, member.getId());
        // "완주 직후 지도에서 내 등수를 본다"(설계 §1-2) — 커밋 후 셀 하나를 지우도록 예약한다
        courseMapCacheEvictor.evictCourseCellAfterCommit(courseId);
        publishCourseRunEvent(running);
        return running;
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
