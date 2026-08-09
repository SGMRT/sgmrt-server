package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Optional;

/**
 * 코스 구독(중간테이블) 쓰기의 <b>단일 지점</b>.
 *
 * <ul>
 *   <li><b>러너 구독</b> — 코스 따라 뛰기 완료 시 {@code RunningWriter}의 저장 트랜잭션 안에서 호출된다.
 *       (구) CourseRunEvent 리스너(BEFORE_COMMIT)를 직접 호출로 전환한 것. (설계 04 §6)</li>
 *   <li><b>주인 구독</b> — 코스 공개/비공개 전환에 맞춰 {@code CourseWriter}가 활성/해제를 위임한다.</li>
 * </ul>
 *
 * <p><b>두 진입점의 멱등 규칙이 다르다.</b> 해제된(soft delete) 구독을 만났을 때 러너 구독
 * ({@link #subscribeIfAbsent})은 <b>그대로 둔다</b>(복원하지 않는다). 반면 주인 구독
 * ({@link #activateOwnerSubscription})은 <b>복원한다</b> — 주인 구독의 soft delete는 코스 비공개 전환을
 * 뜻하고, 재공개 시 되살아나는 것이 {@link CourseSubscription}의 생명주기 계약이기 때문이다.
 * 두 흐름의 조회·저장 모양이 닮았다고 하나로 합치면 이 규칙 차이가 플래그 뒤로 숨는다.
 *
 * <p>자체 트랜잭션을 열지 않는다 — 호출자(CourseWriter / RunningWriter)의 트랜잭션에 참여한다.
 *
 * <p>설계 문서: docs/design/reader-writer-layering.md §4 D2·D3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSubscriptionWriter {

    private final CourseSubscriptionRepository subscriptionRepository;
    private final CourseRepository courseRepository;
    private final MemberService memberService;

    /**
     * 러너 구독 — 구독이 없으면 생성한다. 이미 있으면 아무것도 하지 않는다.
     * 해제된(soft delete) 구독이어도 <b>복원하지 않는다</b>는 점이 {@link #activateOwnerSubscription}과 다르다.
     */
    public void subscribeIfAbsent(Long courseId, Long memberId) {
        Optional<CourseSubscription> existingSubscription =
                subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId);
        if (existingSubscription.isPresent()) {
            log.debug("Subscription already exists: courseId={}, memberId={}", courseId, memberId);
            return;
        }
        createSubscription(courseId, memberId);
    }

    private void createSubscription(Long courseId, Long memberId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, courseId));
        Member member = memberService.findMemberById(memberId);

        subscriptionRepository.save(CourseSubscription.create(course, member));
        log.info("Created new subscription: courseId={}, memberId={}", courseId, memberId);
    }

    /**
     * 주인 구독 — 코스 공개 전환에 맞춰 주인의 구독을 활성 상태로 만든다.
     * <ul>
     *   <li>구독이 없으면 생성 (최초 등록)</li>
     *   <li>해제된 구독이면 복원 (재등록) — 유니크 제약 때문에 재삽입이 아니라 복원이어야 한다</li>
     *   <li>이미 활성이면 그대로 둔다</li>
     * </ul>
     */
    public void activateOwnerSubscription(Course course) {
        Long courseId = course.getId();
        Long ownerId = course.getMember().getId();

        Optional<CourseSubscription> existingSubscription =
                subscriptionRepository.findByCourseIdAndMemberId(courseId, ownerId);

        if (existingSubscription.isEmpty()) {
            subscriptionRepository.save(CourseSubscription.create(course, course.getMember()));
            log.info("Created new subscription for course={}, member={}", courseId, ownerId);
            return;
        }

        CourseSubscription subscription = existingSubscription.get();
        if (subscription.isDeleted()) {
            subscription.restore();
            subscriptionRepository.save(subscription);
            log.info("Restored subscription for course={}, member={}", courseId, ownerId);
        } else {
            log.debug("Subscription already active for course={}, member={}", courseId, ownerId);
        }
    }

    /**
     * 주인 구독 — 코스 비공개 전환에 맞춰 주인의 구독을 soft delete 한다.
     * 구독이 없으면 아무 일도 하지 않으며, 타인(러너)의 구독은 건드리지 않는다.
     */
    public void deactivateOwnerSubscription(Course course) {
        Long courseId = course.getId();
        Long ownerId = course.getMember().getId();

        subscriptionRepository.findByCourseIdAndMemberId(courseId, ownerId)
                .ifPresent(subscription -> {
                    subscription.unregister();
                    subscriptionRepository.save(subscription);
                    log.info("Unregistered subscription for course={}, member={}", courseId, ownerId);
                });
    }
}
