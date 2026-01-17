package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseSubscriptionEventListener {

    private final CourseSubscriptionRepository subscriptionRepository;
    private final CourseRepository courseRepository;
    private final MemberRepository memberRepository;

    /**
     * 코스 따라 뛰기 완료 시 중간테이블 생성
     * - 중간테이블이 없으면 생성
     * - 이미 있으면 아무것도 안함 (멱등성)
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCourseRun(CourseRunEvent event) {
        Long courseId = event.courseId();
        Long memberId = event.runnerId();

        log.debug("CourseRunEvent received: courseId={}, memberId={}", courseId, memberId);

        Optional<CourseSubscription> existingSubscription =
                subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId);

        if (existingSubscription.isEmpty()) {
            createSubscription(courseId, memberId);
        } else {
            log.debug("Subscription already exists: courseId={}, memberId={}", courseId, memberId);
        }
    }

    /**
     * 새로운 중간테이블 생성
     */
    private void createSubscription(Long courseId, Long memberId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(ErrorCode.COURSE_NOT_FOUND, courseId));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        CourseSubscription newSubscription = CourseSubscription.create(course, member);
        subscriptionRepository.save(newSubscription);

        log.info("Created new subscription: courseId={}, memberId={}", courseId, memberId);
    }
}
