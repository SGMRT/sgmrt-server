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
import org.springframework.test.util.ReflectionTestUtils;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.request.CoursePatchRequest;
import soma.ghostrunner.domain.course.exception.CourseAccessDeniedException;
import soma.ghostrunner.domain.course.exception.CourseNameNotValidException;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@DisplayName("CourseWriter 단위 테스트")
@ExtendWith(MockitoExtension.class)
class CourseWriterUnitTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSubscriptionWriter subscriptionWriter;

    @Mock
    private CourseReadModelWriter readModelWriter;

    @Mock
    private CourseMapCacheEvictor mapCacheEvictor;

    @InjectMocks
    private CourseWriter courseWriter;

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
        assignIds(course, COURSE_ID, owner, MEMBER_ID);

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

    /**
     * 구독 테이블 쓰기 자체(생성·복원·soft delete)는 {@link CourseSubscriptionWriter}의 책임이고
     * 그 규칙은 CourseSubscriptionWriterUnitTest가 검증한다. (설계 문서 reader-writer-layering §4 D2)
     *
     * 여기서 지키는 계약은 두 가지다 —
     * (1) 코스의 공개 상태 전환과 주인 구독 위임이 <b>함께</b> 일어난다,
     * (2) 전환이 성립하지 않는 경우(검증 실패·이미 원하는 상태)에는 위임이 <b>일어나지 않는다</b>.
     */
    @Nested
    @DisplayName("주인 구독 위임 (공개 여부 전환)")
    class OwnerSubscriptionDelegation {

        @Test
        @DisplayName("비공개→공개 전환은 주인 구독 활성 위임과 코스 공개 전환이 함께 일어난다")
        void publicize_activatesOwnerSubscriptionAndMakesCoursePublic() {
            // when
            courseWriter.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            then(subscriptionWriter).should().activateOwnerSubscription(course);
        }

        @Test
        @DisplayName("공개→비공개 전환은 주인 구독 해제 위임과 코스 비공개 전환이 함께 일어난다")
        void privatize_deactivatesOwnerSubscriptionAndMakesCoursePrivate() {
            // given
            course.setIsPublic(true);

            // when
            courseWriter.updateCourse(COURSE_ID, publicityRequest(false), owner.getUuid());

            // then
            assertThat(course.isPublic()).isFalse();
            then(subscriptionWriter).should().deactivateOwnerSubscription(course);
        }

        @Test
        @DisplayName("이름 없는 코스는 공개로 전환되지 않으며 주인 구독도 건드리지 않는다 (검증이 활성보다 앞선다)")
        void publicizeWithoutName_failsBeforeTouchingSubscription() {
            // given
            course.setName(null);

            // when & then
            assertThatThrownBy(() ->
                    courseWriter.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid()))
                    .isInstanceOf(CourseNameNotValidException.class);

            assertThat(course.isPublic()).isFalse();
            then(subscriptionWriter).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이미 원하는 공개 상태면 구독 위임 없이 리드모델 동기화만 멱등하게 수행한다")
        void alreadyPublic_skipsSubscriptionButStillSyncsReadModel() {
            // given
            course.setIsPublic(true);

            // when
            courseWriter.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            then(subscriptionWriter).shouldHaveNoInteractions();
            then(readModelWriter).should().syncPublicity(COURSE_ID, true);
        }

        @Test
        @DisplayName("이름만 바꾸는 요청은 주인 구독을 건드리지 않는다")
        void nameOnlyUpdate_doesNotTouchSubscription() {
            // when
            courseWriter.updateCourse(COURSE_ID, nameRequest("새로운 이름"), owner.getUuid());

            // then
            assertThat(course.getName()).isEqualTo("새로운 이름");
            then(subscriptionWriter).shouldHaveNoInteractions();
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
            courseWriter.updateCourse(COURSE_ID, nameRequest("새로운 이름"), owner.getUuid());

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
            assertThatThrownBy(() -> courseWriter.updateCourse(COURSE_ID, request, otherMemberUuid))
                    .isInstanceOf(CourseAccessDeniedException.class)
                    .hasMessageContaining("소유자가 아닙니다");
        }

        @Test
        @DisplayName("코스 삭제 시 소유자가 아니면 CourseAccessDeniedException이 발생한다")
        void deleteCourse_ByNonOwner_ThrowsException() {
            // given
            String otherMemberUuid = "other-member-uuid";

            // when & then
            assertThatThrownBy(() -> courseWriter.deleteCourse(COURSE_ID, otherMemberUuid))
                    .isInstanceOf(CourseAccessDeniedException.class)
                    .hasMessageContaining("소유자가 아닙니다");
        }
    }

    /**
     * 리드모델 조작은 CourseWriter가 직접 하지 않고 {@link CourseReadModelWriter}에 위임한다.
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
            courseWriter.updateCourse(COURSE_ID, nameRequest("새로운 이름"), owner.getUuid());

            // then
            assertThat(course.getName()).isEqualTo("새로운 이름");
            then(readModelWriter).should(times(1)).rename(COURSE_ID, "새로운 이름");
            then(readModelWriter).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("코스 공개 여부를 변경하면 Writer에 공개 상태 동기화를 위임한다")
        void updateCourse_isPublic_delegatesSyncPublicityToWriter() {
            // when
            courseWriter.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());

            // then
            assertThat(course.isPublic()).isTrue();
            then(readModelWriter).should(times(1)).syncPublicity(COURSE_ID, true);
            then(readModelWriter).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("코스를 삭제하면 리드모델 삭제를 Writer에 위임한 뒤 코스를 삭제한다")
        void deleteCourse_delegatesDeleteToWriterBeforeDeletingCourse() {
            // when
            courseWriter.deleteCourse(COURSE_ID, owner.getUuid());

            // then
            InOrder inOrder = inOrder(readModelWriter, courseRepository);
            inOrder.verify(readModelWriter).delete(COURSE_ID);
            inOrder.verify(courseRepository).delete(course);
            then(readModelWriter).shouldHaveNoMoreInteractions();
        }
    }

    /**
     * 셀 버킷 캐시의 이빅트는 커밋 후 좌표만으로 이뤄진다. 코스 삭제 후에는 리드모델이 없어
     * courseId로 셀을 역산할 수 없으므로(M2), 코스가 아직 살아 있는 동안 좌표를 넘기는 것이 계약의 핵심이다.
     *
     * 설계 문서: docs/design/course-cell-bucket-cache-design.md §3-5 · §4(경로 a·b~d) · D4
     */
    @Nested
    @DisplayName("지도 셀 캐시 이빅트 예약")
    class MapCellEviction {

        private static final Double START_LAT = 37.123;
        private static final Double START_LNG = 127.123;

        @Test
        @DisplayName("코스 삭제와 코스 수정은 코스 시작점 좌표와 함께 지도 셀 이빅트를 예약한다")
        void schedulesEvictionWithStartCoordinate() {
            // when
            courseWriter.deleteCourse(COURSE_ID, owner.getUuid());                          // 경로 a
            courseWriter.updateCourse(COURSE_ID, nameRequest("새로운 이름"), owner.getUuid());  // 경로 b~d (이름)
            courseWriter.updateCourse(COURSE_ID, publicityRequest(true), owner.getUuid());  // 경로 b~d (공개)

            // then : 삭제 후에는 되찾을 수 없는 좌표가 그대로 실려 나가야 한다
            then(mapCacheEvictor).should(times(3))
                    .evictCellAfterCommit(COURSE_ID, START_LAT, START_LNG);
            then(mapCacheEvictor).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("이름과 공개 여부를 동시에 바꿔도 이빅트는 정확히 한 번만 예약한다")
        void schedulesExactlyOnceWhenNameAndPublicityChangeTogether() {
            // given
            CoursePatchRequest request = new CoursePatchRequest();
            request.setName("새로운 이름");
            request.setIsPublic(true);

            // when
            courseWriter.updateCourse(COURSE_ID, request, owner.getUuid());

            // then
            assertThat(course.getName()).isEqualTo("새로운 이름");
            assertThat(course.isPublic()).isTrue();
            then(mapCacheEvictor).should(times(1))
                    .evictCellAfterCommit(COURSE_ID, START_LAT, START_LNG);
            then(mapCacheEvictor).shouldHaveNoMoreInteractions();
        }
    }

    /**
     * 영속화되지 않은 엔티티에 ID를 심는다. (id는 DB가 채우는 값이라 팩토리로는 넣을 수 없다)
     */
    private void assignIds(Course course, Long courseId, Member member, Long memberId) {
        ReflectionTestUtils.setField(course, "id", courseId);
        ReflectionTestUtils.setField(member, "id", memberId);
    }
}
