package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.course.exception.CourseNotFoundException;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Optional;

/**
 * 러너의 코스 구독(중간테이블) 관리.
 *
 * (구) CourseRunEvent 리스너(BEFORE_COMMIT)를 직접 호출로 전환한 것 — 코스 따라 뛰기 완료 시
 * 러닝 저장 트랜잭션 안에서 호출된다. (설계 04 §6. 코스 주인의 구독은 CourseService 가 담당)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSubscriptionService {

    private final CourseSubscriptionRepository subscriptionRepository;
    private final CourseRepository courseRepository;
    private final MemberRepository memberRepository;

    /**
     * 구독이 없으면 생성한다. 이미 있으면(soft delete 포함) 아무것도 하지 않는다 — 멱등.
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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        subscriptionRepository.save(CourseSubscription.create(course, member));
        log.info("Created new subscription: courseId={}, memberId={}", courseId, memberId);
    }
}
