package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.CourseSubscriptionRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseSubscription;
import soma.ghostrunner.domain.course.dto.CourseMapper;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.exception.CourseAccessDeniedException;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@DisplayName("CourseService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseServiceUnitTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSubscriptionRepository subscriptionRepository;

    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private CourseService courseService;

    private Member owner;
    private Course course;

    @BeforeEach
    void setUp() {
        owner = Member.of("원작자", "profile.url");
        course = Course.of(owner, 5.0, 10.0, 100.0, -50.0,
                37.123, 127.123, "route.url", "checkpoint.url", "thumb.url");
        course.setName("테스트 코스");
    }

    @Nested
    @DisplayName("코스 등록 (isPublic: false → true)")
    class RegisterCourse {

        @Test
        @DisplayName("처음 등록하는 경우 CourseSubscription을 새로 생성한다")
        void registerCourse_CreatesNewSubscription() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.empty());
            given(courseRepository.save(any(Course.class))).willReturn(course);

            CoursePatchRequest request = new CoursePatchRequest();
            request.setIsPublic(true);

            // when
            courseService.updateCourse(courseId, request, owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            then(subscriptionRepository).should().save(any(CourseSubscription.class));
            then(courseRepository).should().save(course);
        }

        @Test
        @DisplayName("재등록하는 경우 (deleted=true) CourseSubscription을 복원한다")
        void registerCourse_RestoresDeletedSubscription() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            CourseSubscription deletedSubscription = CourseSubscription.create(course, owner);
            deletedSubscription.unregister(); // deleted = true

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.of(deletedSubscription));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            CoursePatchRequest request = new CoursePatchRequest();
            request.setIsPublic(true);

            // when
            courseService.updateCourse(courseId, request, owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            assertThat(deletedSubscription.isActive()).isTrue();
            then(subscriptionRepository).should().save(deletedSubscription);
            then(courseRepository).should().save(course);
        }

        @Test
        @DisplayName("이미 활성화된 CourseSubscription이 있으면 그대로 유지한다")
        void registerCourse_KeepsActiveSubscription() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            CourseSubscription activeSubscription = CourseSubscription.create(course, owner);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.of(activeSubscription));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            CoursePatchRequest request = new CoursePatchRequest();
            request.setIsPublic(true);

            // when
            courseService.updateCourse(courseId, request, owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            assertThat(activeSubscription.isActive()).isTrue();
            // 이미 활성 상태이므로 save 호출 안됨
            then(subscriptionRepository).should(never()).save(activeSubscription);
            then(courseRepository).should().save(course);
        }
    }

    @Nested
    @DisplayName("코스 등록 해제 (isPublic: true → false)")
    class UnregisterCourse {

        @BeforeEach
        void setUpPublicCourse() {
            course.setIsPublic(true);
        }

        @Test
        @DisplayName("등록 해제 시 CourseSubscription의 deleted가 true가 된다")
        void unregisterCourse_SoftDeletesSubscription() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            CourseSubscription activeSubscription = CourseSubscription.create(course, owner);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.of(activeSubscription));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            CoursePatchRequest request = new CoursePatchRequest();
            request.setIsPublic(false);

            // when
            courseService.updateCourse(courseId, request, owner.getUuid());

            // then
            assertThat(course.isPublic()).isFalse();
            assertThat(activeSubscription.isDeleted()).isTrue();
            then(subscriptionRepository).should().save(activeSubscription);
            then(courseRepository).should().save(course);
        }

        @Test
        @DisplayName("CourseSubscription이 없어도 등록 해제가 정상 동작한다")
        void unregisterCourse_WithoutSubscription() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.empty());
            given(courseRepository.save(any(Course.class))).willReturn(course);

            CoursePatchRequest request = new CoursePatchRequest();
            request.setIsPublic(false);

            // when
            courseService.updateCourse(courseId, request, owner.getUuid());

            // then
            assertThat(course.isPublic()).isFalse();
            then(subscriptionRepository).should(never()).save(any(CourseSubscription.class));
            then(courseRepository).should().save(course);
        }
    }

    @Nested
    @DisplayName("전체 시나리오")
    class FullScenario {

        @Test
        @DisplayName("등록 → 해제 → 재등록 시나리오가 정상 동작한다")
        void fullCycle_RegisterUnregisterReregister() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when 1 - 등록
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.empty());

            CoursePatchRequest registerRequest = new CoursePatchRequest();
            registerRequest.setIsPublic(true);
            courseService.updateCourse(courseId, registerRequest, owner.getUuid());

            // then 1
            assertThat(course.isPublic()).isTrue();
            then(subscriptionRepository).should(times(1)).save(any(CourseSubscription.class));

            // when 2 - 등록 해제
            CourseSubscription subscription = CourseSubscription.create(course, owner);
            given(subscriptionRepository.findByCourseIdAndMemberId(courseId, memberId))
                    .willReturn(Optional.of(subscription));

            CoursePatchRequest unregisterRequest = new CoursePatchRequest();
            unregisterRequest.setIsPublic(false);
            courseService.updateCourse(courseId, unregisterRequest, owner.getUuid());

            // then 2
            assertThat(course.isPublic()).isFalse();
            assertThat(subscription.isDeleted()).isTrue();

            // when 3 - 재등록
            CoursePatchRequest reregisterRequest = new CoursePatchRequest();
            reregisterRequest.setIsPublic(true);
            courseService.updateCourse(courseId, reregisterRequest, owner.getUuid());

            // then 3
            assertThat(course.isPublic()).isTrue();
            assertThat(subscription.isActive()).isTrue();
            then(courseRepository).should(times(3)).save(course);
        }
    }

    @Nested
    @DisplayName("코스 소유자 검증")
    class OwnerVerification {

        @Test
        @DisplayName("코스 소유자가 수정하면 정상 동작한다")
        void updateCourse_ByOwner_Success() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            CoursePatchRequest request = new CoursePatchRequest();
            request.setName("새로운 이름");

            // when
            courseService.updateCourse(courseId, request, owner.getUuid());

            // then
            then(courseRepository).should().save(course);
        }

        @Test
        @DisplayName("코스 소유자가 아닌 사람이 수정하면 CourseAccessDeniedException이 발생한다")
        void updateCourse_ByNonOwner_ThrowsException() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

            CoursePatchRequest request = new CoursePatchRequest();
            request.setName("새로운 이름");
            String otherMemberUuid = "other-member-uuid";

            // when & then
            assertThatThrownBy(() -> courseService.updateCourse(courseId, request, otherMemberUuid))
                    .isInstanceOf(CourseAccessDeniedException.class)
                    .hasMessageContaining("소유자가 아닙니다");
        }

        @Test
        @DisplayName("코스 삭제 시 소유자가 아니면 CourseAccessDeniedException이 발생한다")
        void deleteCourse_ByNonOwner_ThrowsException() {
            // given
            Long courseId = 1L;
            Long memberId = 1L;
            setIds(course, courseId, owner, memberId);

            given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

            String otherMemberUuid = "other-member-uuid";

            // when & then
            assertThatThrownBy(() -> courseService.deleteCourse(courseId, otherMemberUuid))
                    .isInstanceOf(CourseAccessDeniedException.class)
                    .hasMessageContaining("소유자가 아닙니다");
        }
    }

    /**
     * 테스트를 위한 ID 설정 헬퍼 메서드
     */
    private void setIds(Course course, Long courseId, Member member, Long memberId) {
        try {
            // Reflection을 사용해 private id 필드에 접근
            java.lang.reflect.Field courseIdField = Course.class.getDeclaredField("id");
            courseIdField.setAccessible(true);
            courseIdField.set(course, courseId);

            java.lang.reflect.Field memberIdField = Member.class.getDeclaredField("id");
            memberIdField.setAccessible(true);
            memberIdField.set(member, memberId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set IDs via reflection", e);
        }
    }
}
