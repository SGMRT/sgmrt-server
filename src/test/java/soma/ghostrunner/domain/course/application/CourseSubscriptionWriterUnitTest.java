package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 구독 테이블 쓰기의 <b>단일 지점</b>인 {@link CourseSubscriptionWriter}의 단위 테스트.
 * 주인 구독(활성/복원/해제)과 러너 구독(subscribeIfAbsent)이 지켜야 할 멱등·소프트delete 규칙을 못박는다.
 *
 * 설계 문서: docs/design/reader-writer-layering.md §4 D2·D3
 */
@DisplayName("CourseSubscriptionWriter 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseSubscriptionWriterUnitTest {

    @Mock
    private CourseSubscriptionRepository subscriptionRepository;

    @Mock
    private CourseRepository courseRepository;

    /** D3 — 타 도메인 repo(MemberRepository)가 아니라 MemberService를 경유한다. */
    @Mock
    private MemberService memberService;

    @InjectMocks
    private CourseSubscriptionWriter subscriptionWriter;

    private static final Long COURSE_ID = 1L;
    private static final Long OWNER_ID = 10L;

    private Member owner;
    private Course course;

    @BeforeEach
    void setUp() {
        owner = Member.of("원작자", "profile.url");
        course = Course.of(owner, 5.0, 10.0, 100.0, -50.0,
                37.123, 127.123, "route.url", "checkpoint.url", "thumb.url");
        course.setName("테스트 코스");
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
    }

    @Nested
    @DisplayName("주인 구독 활성화 (코스 공개 전환)")
    class ActivateOwnerSubscription {

        @Test
        @DisplayName("구독이 없으면 주인 본인의 활성 구독을 새로 만든다")
        void createsSubscriptionWhenAbsent() {
            // given
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, OWNER_ID))
                    .willReturn(Optional.empty());

            // when
            subscriptionWriter.activateOwnerSubscription(course);

            // then
            ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
            then(subscriptionRepository).should().save(captor.capture());

            CourseSubscription created = captor.getValue();
            assertThat(created.getCourse()).isSameAs(course);
            assertThat(created.getMember()).isSameAs(owner);
            assertThat(created.isActive()).isTrue();
        }

        @Test
        @DisplayName("해제됐던 구독은 새로 만들지 않고 복원한다 (유니크 제약이 있으므로 재삽입은 불가)")
        void restoresDeletedSubscription() {
            // given
            CourseSubscription deletedSubscription = CourseSubscription.create(course, owner);
            deletedSubscription.unregister();

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, OWNER_ID))
                    .willReturn(Optional.of(deletedSubscription));

            // when
            subscriptionWriter.activateOwnerSubscription(course);

            // then
            assertThat(deletedSubscription.isActive()).isTrue();
            then(subscriptionRepository).should().save(deletedSubscription);
        }

        @Test
        @DisplayName("이미 활성인 구독이면 쓰기 없이 그대로 둔다")
        void keepsAlreadyActiveSubscription() {
            // given
            CourseSubscription activeSubscription = CourseSubscription.create(course, owner);

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, OWNER_ID))
                    .willReturn(Optional.of(activeSubscription));

            // when
            subscriptionWriter.activateOwnerSubscription(course);

            // then
            assertThat(activeSubscription.isActive()).isTrue();
            then(subscriptionRepository).should(never()).save(any(CourseSubscription.class));
        }
    }

    @Nested
    @DisplayName("주인 구독 해제 (코스 비공개 전환)")
    class DeactivateOwnerSubscription {

        @Test
        @DisplayName("구독을 지우지 않고 soft delete 상태로 만든다")
        void softDeletesSubscription() {
            // given
            CourseSubscription activeSubscription = CourseSubscription.create(course, owner);

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, OWNER_ID))
                    .willReturn(Optional.of(activeSubscription));

            // when
            subscriptionWriter.deactivateOwnerSubscription(course);

            // then
            assertThat(activeSubscription.isDeleted()).isTrue();
            then(subscriptionRepository).should().save(activeSubscription);
        }

        @Test
        @DisplayName("구독이 없으면 아무 일도 하지 않는다")
        void doesNothingWhenSubscriptionAbsent() {
            // given
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, OWNER_ID))
                    .willReturn(Optional.empty());

            // when
            subscriptionWriter.deactivateOwnerSubscription(course);

            // then
            then(subscriptionRepository).should(never()).save(any(CourseSubscription.class));
        }
    }

    @Nested
    @DisplayName("러너 구독 (코스 따라 뛰기)")
    class SubscribeIfAbsent {

        private static final Long RUNNER_ID = 20L;

        /** 코스를 따라 뛴 사람은 주인이 아닌 타인이다. */
        private Member runner;

        @BeforeEach
        void setUpRunner() {
            runner = Member.of("따라 뛴 러너", "runner.url");
            ReflectionTestUtils.setField(runner, "id", RUNNER_ID);
        }

        @Test
        @DisplayName("구독이 없으면 새로 만든다")
        void createsSubscriptionWhenAbsent() {
            // given
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, RUNNER_ID))
                    .willReturn(Optional.empty());
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
            given(memberService.findMemberById(RUNNER_ID)).willReturn(runner);

            // when
            subscriptionWriter.subscribeIfAbsent(COURSE_ID, RUNNER_ID);

            // then
            ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
            then(subscriptionRepository).should().save(captor.capture());

            CourseSubscription created = captor.getValue();
            assertThat(created.getCourse()).isSameAs(course);
            assertThat(created.getMember()).isSameAs(runner);
        }

        @Test
        @DisplayName("구독이 이미 있으면 아무것도 하지 않는다 — soft delete 상태여도 복원하지 않는다 (멱등)")
        void doesNothingWhenSubscriptionExists() {
            // given : 해제됐던 구독이라도 러너 구독은 되살리지 않는다
            CourseSubscription deletedSubscription = CourseSubscription.create(course, runner);
            deletedSubscription.unregister();

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, RUNNER_ID))
                    .willReturn(Optional.of(deletedSubscription));

            // when
            subscriptionWriter.subscribeIfAbsent(COURSE_ID, RUNNER_ID);

            // then
            assertThat(deletedSubscription.isDeleted()).isTrue();
            then(subscriptionRepository).should(never()).save(any(CourseSubscription.class));
            then(courseRepository).shouldHaveNoInteractions();
            then(memberService).shouldHaveNoInteractions();
        }

        /**
         * D3 — MemberRepository 직접 접근을 MemberService 경유로 바꿔도
         * "없는 회원이면 MEMBER_NOT_FOUND로 실패하고 구독은 남지 않는다"는 계약은 유지돼야 한다.
         */
        @Test
        @DisplayName("존재하지 않는 회원의 구독 요청은 MEMBER_NOT_FOUND로 실패하고 구독을 남기지 않는다")
        void failsWithMemberNotFoundWhenMemberAbsent() {
            // given
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, RUNNER_ID))
                    .willReturn(Optional.empty());
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
            given(memberService.findMemberById(RUNNER_ID))
                    .willThrow(new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> subscriptionWriter.subscribeIfAbsent(COURSE_ID, RUNNER_ID))
                    .isInstanceOf(MemberNotFoundException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

            then(subscriptionRepository).should(never()).save(any(CourseSubscription.class));
        }
    }
}
