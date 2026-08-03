package soma.ghostrunner.domain.course.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CourseSubscriptionRepository 통합 테스트")
class CourseSubscriptionRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private CourseSubscriptionRepository subscriptionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member member;
    private Course course;

    @BeforeEach
    void setUp() {
        member = Member.of("테스트유저", "profile.url");
        memberRepository.save(member);

        course = Course.of(member, 5.0, 10.0, 100.0, -50.0,
                37.123, 127.123, "route.url", "checkpoint.url", "thumb.url");
        course.setName("테스트 코스");
        courseRepository.save(course);
    }

    @Test
    @DisplayName("CourseSubscription을 저장하고 조회할 수 있다")
    void save_AndFindById() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);

        // when
        CourseSubscription saved = subscriptionRepository.save(subscription);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCourse()).isEqualTo(course);
        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("courseId와 memberId로 CourseSubscription을 조회할 수 있다")
    void findByCourseIdAndMemberId() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);
        subscriptionRepository.save(subscription);

        // when
        Optional<CourseSubscription> found = subscriptionRepository
                .findByCourseIdAndMemberId(course.getId(), member.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getCourse().getId()).isEqualTo(course.getId());
        assertThat(found.get().getMember().getId()).isEqualTo(member.getId());
    }

    @Test
    @DisplayName("존재하지 않는 courseId와 memberId 조합은 빈 Optional을 반환한다")
    void findByCourseIdAndMemberId_NotFound() {
        // when
        Optional<CourseSubscription> found = subscriptionRepository
                .findByCourseIdAndMemberId(999L, 999L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("특정 코스의 활성 구독자 수를 조회할 수 있다")
    void countActiveByCourseId() {
        // given
        Member member1 = Member.of("유저1", "url1");
        Member member2 = Member.of("유저2", "url2");
        Member member3 = Member.of("유저3", "url3");
        memberRepository.saveAll(java.util.List.of(member1, member2, member3));

        CourseSubscription sub1 = CourseSubscription.create(course, member1);
        CourseSubscription sub2 = CourseSubscription.create(course, member2);
        CourseSubscription sub3 = CourseSubscription.create(course, member3);
        sub3.unregister(); // 하나는 등록 해제

        subscriptionRepository.saveAll(java.util.List.of(sub1, sub2, sub3));

        // when
        Long activeCount = subscriptionRepository.countActiveByCourseId(course.getId());

        // then
        assertThat(activeCount).isEqualTo(2L); // deleted=false인 것만 카운트
    }

    @Test
    @DisplayName("활성 구독자가 없으면 0을 반환한다")
    void countActiveByCourseId_NoActiveSubscribers() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);
        subscription.unregister();
        subscriptionRepository.save(subscription);

        // when
        Long activeCount = subscriptionRepository.countActiveByCourseId(course.getId());

        // then
        assertThat(activeCount).isEqualTo(0L);
    }

    @Test
    @DisplayName("Soft Delete된 CourseSubscription도 조회할 수 있다")
    void findByCourseIdAndMemberId_IncludesSoftDeleted() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);
        subscription.unregister(); // deleted = true
        subscriptionRepository.save(subscription);

        // when
        Optional<CourseSubscription> found = subscriptionRepository
                .findByCourseIdAndMemberId(course.getId(), member.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("동일한 course와 member 조합에 대해 Unique Constraint가 작동한다")
    void uniqueConstraint_CourseAndMember() {
        // given
        CourseSubscription subscription1 = CourseSubscription.create(course, member);
        subscriptionRepository.save(subscription1);

        // when & then
        CourseSubscription subscription2 = CourseSubscription.create(course, member);
        
        // 동일한 조합이므로 예외 발생 예상
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    subscriptionRepository.save(subscription2);
                    subscriptionRepository.flush(); // Constraint 체크를 위해 flush
                }
        );
    }
}
