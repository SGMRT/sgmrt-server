package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
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
    private CourseReadModelWriter readModelWriter;

    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private CourseService courseService;

    private static final Long COURSE_ID = 1L;
    private static final Long MEMBER_ID = 1L;

    private Member owner;
    private Course course;

    @BeforeEach
    void setUp() {
        owner = Member.of("원작자", "profile.url");
        course = Course.of(owner, 5.0, 10.0, 100.0, -50.0,
                37.123, 127.123, "route.url", "checkpoint.url", "thumb.url");
        course.setName("테스트 코스");
        setIds(course, COURSE_ID, owner, MEMBER_ID);

        // 모든 테스트가 수정/삭제 대상 코스를 조회한다
        given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
    }

    private CoursePatchRequest nameRequest(String name) {
        CoursePatchRequest request = new CoursePatchRequest();
        request.setName(name);
        return request;
    }

    private CoursePatchRequest publicityRequest(boolean isPublic) {
        CoursePatchRequest request = new CoursePatchRequest();
        request.setIsPublic(isPublic);
        return request;
    }

    @Nested
    @DisplayName("코스 등록 (isPublic: false → true)")
    class RegisterCourse {

        @Test
        @DisplayName("처음 등록하는 경우 CourseSubscription을 새로 생성한다")
        void registerCourse_CreatesNewSubscription() {
            // given
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.empty());
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when
            courseService.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            then(subscriptionRepository).should().save(any(CourseSubscription.class));
            then(courseRepository).should().save(course);
        }

        @Test
        @DisplayName("재등록하는 경우 (deleted=true) CourseSubscription을 복원한다")
        void registerCourse_RestoresDeletedSubscription() {
            // given
            CourseSubscription deletedSubscription = CourseSubscription.create(course, owner);
            deletedSubscription.unregister(); // deleted = true

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.of(deletedSubscription));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when
            courseService.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

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
            CourseSubscription activeSubscription = CourseSubscription.create(course, owner);

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.of(activeSubscription));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when
            courseService.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

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
            CourseSubscription activeSubscription = CourseSubscription.create(course, owner);

            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.of(activeSubscription));
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when
            courseService.updateCourse(COURSE_ID, publicityRequest(false), owner.getUuid());

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
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.empty());
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when
            courseService.updateCourse(COURSE_ID, publicityRequest(false), owner.getUuid());

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
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when 1 - 등록
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.empty());

            courseService.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

            // then 1
            assertThat(course.isPublic()).isTrue();
            then(subscriptionRepository).should(times(1)).save(any(CourseSubscription.class));

            // when 2 - 등록 해제
            CourseSubscription subscription = CourseSubscription.create(course, owner);
            given(subscriptionRepository.findByCourseIdAndMemberId(COURSE_ID, MEMBER_ID))
                    .willReturn(Optional.of(subscription));

            courseService.updateCourse(COURSE_ID, publicityRequest(false), owner.getUuid());

            // then 2
            assertThat(course.isPublic()).isFalse();
            assertThat(subscription.isDeleted()).isTrue();

            // when 3 - 재등록
            courseService.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

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
            given(courseRepository.save(any(Course.class))).willReturn(course);

            // when
            courseService.updateCourse(COURSE_ID, nameRequest("새로운 이름"), owner.getUuid());

            // then
            then(courseRepository).should().save(course);
        }

        @Test
        @DisplayName("코스 소유자가 아닌 사람이 수정하면 CourseAccessDeniedException이 발생한다")
        void updateCourse_ByNonOwner_ThrowsException() {
            // given
            CoursePatchRequest request = nameRequest("새로운 이름");
            String otherMemberUuid = "other-member-uuid";

            // when & then
            assertThatThrownBy(() -> courseService.updateCourse(COURSE_ID, request, otherMemberUuid))
                    .isInstanceOf(CourseAccessDeniedException.class)
                    .hasMessageContaining("소유자가 아닙니다");
        }

        @Test
        @DisplayName("코스 삭제 시 소유자가 아니면 CourseAccessDeniedException이 발생한다")
        void deleteCourse_ByNonOwner_ThrowsException() {
            // given
            String otherMemberUuid = "other-member-uuid";

            // when & then
            assertThatThrownBy(() -> courseService.deleteCourse(COURSE_ID, otherMemberUuid))
                    .isInstanceOf(CourseAccessDeniedException.class)
                    .hasMessageContaining("소유자가 아닙니다");
        }
    }

    /**
     * 리드모델 조작은 CourseService가 직접 하지 않고 {@link CourseReadModelWriter}에 위임한다.
     * (설계 문서 §3-2 유즈케이스 표 — 코스명 변경/공개 전환/삭제)
     *
     * 리드모델 자체의 동작(생성·재계산·X락 등)은 CourseReadModelWriterTest가 검증하므로,
     * 여기서는 "무엇을 몇 번 위임하는가"만 본다.
     */
    @Nested
    @DisplayName("리드모델 동기화 위임")
    class ReadModelSync {

        @Test
        @DisplayName("코스명을 변경하면 Writer에 이름 동기화를 위임한다")
        void updateCourse_name_delegatesRenameToWriter() {
            // when
            courseService.updateCourse(COURSE_ID, nameRequest("새로운 이름"), owner.getUuid());

            // then
            assertThat(course.getName()).isEqualTo("새로운 이름");
            then(readModelWriter).should(times(1)).rename(COURSE_ID, "새로운 이름");
            then(readModelWriter).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("코스 공개 여부를 변경하면 Writer에 공개 상태 동기화를 위임한다")
        void updateCourse_isPublic_delegatesSyncPublicityToWriter() {
            // when
            courseService.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            then(readModelWriter).should(times(1)).syncPublicity(COURSE_ID, true);
            then(readModelWriter).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("코스를 삭제하면 리드모델 삭제를 Writer에 위임한 뒤 코스를 삭제한다")
        void deleteCourse_delegatesDeleteToWriterBeforeDeletingCourse() {
            // when
            courseService.deleteCourse(COURSE_ID, owner.getUuid());

            // then
            InOrder inOrder = inOrder(readModelWriter, courseRepository);
            inOrder.verify(readModelWriter).delete(COURSE_ID);
            inOrder.verify(courseRepository).delete(course);
            then(readModelWriter).shouldHaveNoMoreInteractions();
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
