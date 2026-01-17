package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseSubscriptionEventListener 테스트")
class CourseSubscriptionEventListenerTest {

    @InjectMocks
    private CourseSubscriptionEventListener listener;

    @Mock
    private CourseSubscriptionRepository subscriptionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("중간테이블이 없으면 새로 생성한다")
    void handleCourseRun_CreateNewSubscription() {
        // given
        Long courseId = 1L;
        Long memberId = 2L;
        CourseRunEvent event = new CourseRunEvent(
                courseId, "테스트 코스", 100L,
                1L, System.currentTimeMillis(), 3600L,
                memberId, "러너닉네임"
        );

        Course mockCourse = mock(Course.class);
        Member mockMember = mock(Member.class);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(mockMember));
        when(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                .thenReturn(Optional.empty());

        // when
        listener.handleCourseRun(event);

        // then
        verify(subscriptionRepository).findByCourseIdAndMemberId(courseId, memberId);
        verify(subscriptionRepository).save(any(CourseSubscription.class));
    }

    @Test
    @DisplayName("중간테이블이 이미 있으면 아무것도 하지 않는다 (deleted 여부 무관)")
    void handleCourseRun_SubscriptionExists() {
        // given
        Long courseId = 1L;
        Long memberId = 2L;
        CourseRunEvent event = new CourseRunEvent(
                courseId, "테스트 코스", 100L,
                1L, System.currentTimeMillis(), 3600L,
                memberId, "러너닉네임"
        );

        CourseSubscription mockSubscription = mock(CourseSubscription.class);

        when(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                .thenReturn(Optional.of(mockSubscription));

        // when
        listener.handleCourseRun(event);

        // then
        verify(subscriptionRepository).findByCourseIdAndMemberId(courseId, memberId);
        verify(subscriptionRepository, never()).save(any());
        verify(courseRepository, never()).findById(any());
        verify(memberRepository, never()).findById(any());
    }
}
