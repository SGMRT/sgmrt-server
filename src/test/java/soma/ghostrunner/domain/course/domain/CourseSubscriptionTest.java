package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Member;

import static org.assertj.core.api.Assertions.*;

class CourseSubscriptionTest {

    private Member member;
    private Course course;

    @BeforeEach
    void setUp() {
        member = Member.of("러너123", "profile.url");
        course = Course.of(member, 5.0, 10.0, 100.0, -50.0,
                37.123, 127.123, "route.url", "checkpoint.url", "thumb.url");
    }

    @Test
    @DisplayName("정적 팩토리 메서드 create()로 CourseSubscription 객체를 생성한다")
    void createCourseSubscription_Success() {
        // when
        CourseSubscription subscription = CourseSubscription.create(course, member);

        // then
        assertThat(subscription).isNotNull();
        assertThat(subscription.getCourse()).isEqualTo(course);
        assertThat(subscription.getMember()).isEqualTo(member);
        assertThat(subscription.isDeleted()).isFalse();
        assertThat(subscription.isActive()).isTrue();
    }

    @Test
    @DisplayName("unregister()를 호출하면 deleted가 true로 변경된다")
    void unregister_Success() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);
        assertThat(subscription.isDeleted()).isFalse();

        // when
        subscription.unregister();

        // then
        assertThat(subscription.isDeleted()).isTrue();
        assertThat(subscription.isActive()).isFalse();
    }

    @Test
    @DisplayName("unregister()를 여러 번 호출해도 멱등성이 보장된다")
    void unregister_Idempotent() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);

        // when
        subscription.unregister();
        subscription.unregister();
        subscription.unregister();

        // then
        assertThat(subscription.isDeleted()).isTrue();
        assertThat(subscription.isActive()).isFalse();
    }

    @Test
    @DisplayName("restore()를 호출하면 삭제된 구독을 재활성화할 수 있다")
    void restore_Success() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);
        subscription.unregister();
        assertThat(subscription.isDeleted()).isTrue();

        // when
        subscription.restore();

        // then
        assertThat(subscription.isDeleted()).isFalse();
        assertThat(subscription.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive()는 deleted 상태의 반대값을 반환한다")
    void isActive_ReturnsOppositeOfDeleted() {
        // given
        CourseSubscription subscription = CourseSubscription.create(course, member);

        // when & then - 활성 상태
        assertThat(subscription.isActive()).isTrue();
        assertThat(subscription.isDeleted()).isFalse();

        // when & then - 비활성 상태
        subscription.unregister();
        assertThat(subscription.isActive()).isFalse();
        assertThat(subscription.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("코스 등록 해제 후 재등록하는 시나리오가 정상 동작한다")
    void unregisterAndRestore_Scenario() {
        // given - 코스 구독 생성
        CourseSubscription subscription = CourseSubscription.create(course, member);
        assertThat(subscription.isActive()).isTrue();

        // when - 등록 해제
        subscription.unregister();

        // then - 비활성 상태 확인
        assertThat(subscription.isActive()).isFalse();
        assertThat(subscription.isDeleted()).isTrue();

        // when - 재등록
        subscription.restore();

        // then - 활성 상태 확인
        assertThat(subscription.isActive()).isTrue();
        assertThat(subscription.isDeleted()).isFalse();
    }
}
