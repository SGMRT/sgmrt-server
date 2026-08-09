package soma.ghostrunner.domain.running.application;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import soma.ghostrunner.domain.course.application.CourseMapCacheEvictor;
import soma.ghostrunner.domain.course.application.CourseReader;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseSubscriptionWriter;
import soma.ghostrunner.domain.course.application.CourseWriter;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쓰기 트랜잭션 경계({@link RunningWriter})가 <b>무엇을 안에 품는지</b>를 고정한다.
 *
 * <p>여기서 검증하는 호출들은 전부 "트랜잭션 밖으로 나가면 조용히 망가지는" 것들이다 —
 * MANDATORY 인 리드모델 갱신·재계산, 커밋 후 실행을 예약하는 지도 이빅트, AFTER_COMMIT 소비자를 가진 완주 이벤트,
 * 자체 트랜잭션이 없는 구독 생성. 트랜잭션이 실제로 걸리는지 자체는
 * {@code RunningCreationTransactionIntegrationTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RunningWriter 단위 테스트 - 쓰기 트랜잭션이 품는 부수효과")
class RunningWriterTest {

    @Mock RunningApplicationMapper mapper;
    @Mock RunningRepository runningRepository;
    @Mock RunningReader runningReader;
    @Mock CourseWriter courseWriter;
    @Mock CourseReader courseReader;
    @Mock CourseReadModelWriter courseReadModelWriter;
    @Mock CourseSubscriptionWriter courseSubscriptionWriter;
    @Mock CourseMapCacheEvictor courseMapCacheEvictor;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks RunningWriter sut;

    private final String memberUuid = "mem-123";
    private final CreateRunCommand command = mock(CreateRunCommand.class);
    private final TelemetryStatistics stats = mock(TelemetryStatistics.class);
    private final RunningDataUrlsDto urls = mock(RunningDataUrlsDto.class);

    // ====== 생성 ======

    @Test
    @DisplayName("saveRunAndCourse: 코스 저장 → 러닝 저장 → 리드모델 반영 순서로 진행하고, 새 코스의 지도 셀 이빅트를 예약한다")
    void saveRunAndCourse_savesThenAppliesReadModelAndSchedulesEvict() {
        // given
        Member member = mock(Member.class);
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(500L);
        when(mapper.toCourse(member, command, stats, urls)).thenReturn(course);

        Running running = mock(Running.class);
        when(mapper.toRunning(command, stats, urls, member, course)).thenReturn(running);
        when(runningRepository.save(running)).thenReturn(running);

        // when
        RunningWriter.CreatedRun created = sut.saveRunAndCourse(command, member, stats, urls);

        // then
        assertThat(created.running()).isSameAs(running);
        assertThat(created.course()).isSameAs(course);

        // applyRun 은 ID가 채워진 영속 러닝을 요구한다 — 반드시 save 뒤여야 한다
        var inOrder = inOrder(courseWriter, runningRepository, courseReadModelWriter);
        inOrder.verify(courseWriter).save(course);
        inOrder.verify(runningRepository).save(running);
        inOrder.verify(courseReadModelWriter).applyRun(running);

        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(500L);
    }

    /**
     * 코스를 <b>트랜잭션 안에서 다시 조회</b>하는 것이 이 메서드의 계약이다. 호출자가 밖에서 읽은 detached
     * {@code Course}를 그대로 받도록 시그니처를 되돌리면 커밋 시점의 LAZY 접근이 터진다.
     */
    @Test
    @DisplayName("saveRun: 코스를 트랜잭션 안에서 조회해 러닝을 붙이고, 리드모델·구독·이빅트·완주 이벤트를 함께 처리한다")
    void saveRun_refetchesCourseAndFiresAllInTransactionSideEffects() {
        // given
        long courseId = 77L;
        long memberId = 5L;

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);

        Course course = mock(Course.class);
        when(courseReader.findCourseByIdFetchJoinMember(courseId)).thenReturn(course);

        Running running = mock(Running.class);
        CourseRunEvent event = new CourseRunEvent(courseId, "한강 코스", 9L, 100L, 0L, 1800L, memberId, "러너");
        when(running.createCourseRunEvent()).thenReturn(event);
        when(mapper.toRunning(command, stats, urls, member, course)).thenReturn(running);
        when(runningRepository.save(running)).thenReturn(running);

        // when
        Running saved = sut.saveRun(command, member, courseId, stats, urls);

        // then
        assertThat(saved).isSameAs(running);
        verify(courseReader).findCourseByIdFetchJoinMember(courseId);
        verify(courseReadModelWriter, times(1)).applyRun(running);
        verify(courseSubscriptionWriter, times(1)).subscribeIfAbsent(courseId, memberId);
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(courseId);
        // AFTER_COMMIT 소비자(PushEventListener)가 있으므로 발행은 반드시 트랜잭션 안이어야 한다
        verify(eventPublisher, times(1)).publishEvent(eq(event));
    }

    @Test
    @DisplayName("saveRun: 집계 대상 판정은 하지 않고 저장된 러닝을 그대로 Writer 에 위임한다")
    void saveRun_delegatesFilteringToReadModelWriter() {
        // given : 일시정지 러닝이어도 이 클래스는 거르지 않는다 (제외 판정은 CourseReadModelWriter 책임)
        long courseId = 77L;
        Member member = mock(Member.class);
        Course course = mock(Course.class);
        when(courseReader.findCourseByIdFetchJoinMember(courseId)).thenReturn(course);

        Running pausedRunning = mock(Running.class);
        when(pausedRunning.createCourseRunEvent())
                .thenReturn(new CourseRunEvent(courseId, "한강 코스", 9L, 101L, 0L, 1800L, 5L, "러너"));
        when(mapper.toRunning(command, stats, urls, member, course)).thenReturn(pausedRunning);
        when(runningRepository.save(pausedRunning)).thenReturn(pausedRunning);

        // when
        sut.saveRun(command, member, courseId, stats, urls);

        // then
        verify(courseReadModelWriter, times(1)).applyRun(pausedRunning);
        verify(pausedRunning, times(0)).isHasPaused();
    }

    // ====== 수정 ======

    @Test
    @DisplayName("updateName: 본인 검증 후 이름을 변경하고, 코스의 지도 셀 이빅트를 예약한다")
    void updateName_updatesAfterOwnershipCheck() {
        // given
        Long runningId = 10L;
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(40L);

        Running running = mock(Running.class);
        when(running.getCourse()).thenReturn(course);
        when(runningReader.findRunningByRunningId(runningId)).thenReturn(running);

        // when
        sut.updateName("새 이름", runningId, memberUuid);

        // then
        InOrder inOrder = inOrder(runningReader, running);
        inOrder.verify(runningReader).findRunningByRunningId(runningId);
        inOrder.verify(running).verifyMember(memberUuid);
        inOrder.verify(running).updateName("새 이름");

        // 러닝 이름은 지도 카드에 노출되므로 셀 캐시도 커밋 후 지워야 한다
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(40L);
    }

    @Test
    @DisplayName("updatePublicStatus: 본인 검증 후 공개 상태를 토글하고, 코스를 재계산하고, 지도 셀 이빅트를 예약한다")
    void updatePublicStatus_togglesRecalculatesAndSchedulesEvict() {
        // given
        Long runningId = 11L;
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(30L);

        Running running = mock(Running.class);
        when(running.getCourse()).thenReturn(course);
        when(runningReader.findRunningByRunningId(runningId)).thenReturn(running);

        // when
        sut.updatePublicStatus(runningId, memberUuid);

        // then
        InOrder inOrder = inOrder(runningReader, running);
        inOrder.verify(runningReader).findRunningByRunningId(runningId);
        inOrder.verify(running).verifyMember(memberUuid);
        inOrder.verify(running).updatePublicStatus();

        // 공개 여부가 바뀌면 집계 모집단이 달라지므로 리드모델을 재계산한다
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(courseReadModelWriter, times(1)).recalculate(captor.capture());
        assertThat(captor.getValue()).containsExactly(30L);

        // 지도 셀 이빅트는 직접 호출로 예약된다
        verify(courseMapCacheEvictor, times(1)).evictCourseCellAfterCommit(30L);
    }

    // ====== 삭제 ======

    @Test
    @DisplayName("deleteRunnings: 각 러닝에 대해 소유자 검증 후 일괄 삭제 요청한다")
    void deleteRunnings_verifiesOwnershipThenDeletes() {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);
        Running r1 = mock(Running.class);
        Running r2 = mock(Running.class);
        Running r3 = mock(Running.class);

        when(runningRepository.findByIds(ids)).thenReturn(List.of(r1, r2, r3));

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        verify(r1).verifyMember(memberUuid);
        verify(r2).verifyMember(memberUuid);
        verify(r3).verifyMember(memberUuid);
        verify(runningRepository).deleteInRunningIds(ids);
    }

    @Test
    @DisplayName("deleteRunnings: 삭제된 러닝들의 코스를 중복 없이 모아 한 번에 재계산한다")
    void deleteRunnings_recalculatesDistinctCourses() {
        // given : 같은 코스의 러닝 2건 + 다른 코스의 러닝 1건
        List<Long> ids = List.of(1L, 2L, 3L);

        Course courseA = mock(Course.class);
        when(courseA.getId()).thenReturn(10L);
        Course courseB = mock(Course.class);
        when(courseB.getId()).thenReturn(20L);

        givenRunningsOnCourses(ids, courseA, courseA, courseB);

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(courseReadModelWriter, times(1)).recalculate(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    /**
     * 셀 버킷 캐시는 좌표로만 이빅트하므로, 삭제로 TOP4가 바뀐 코스는 좌표와 함께 이빅트를 예약해야 한다(M1).
     * 좌표 수집은 반드시 벌크 삭제 이전이어야 한다 — deleteInRunningIds 가
     * {@code @Modifying(clearAutomatically = true)} 라 그 뒤에는 LAZY Course 프록시가
     * 미초기화 상태로 detach 되어 초기화할 수 없다([R2]). 좌표 추출을 삭제 뒤로 옮기면 이 테스트가 빨개진다.
     *
     * 설계 문서: docs/design/course-cell-bucket-cache-design.md §4(경로 e) · [R2] · D5
     */
    @Test
    @DisplayName("deleteRunnings: 영향받은 코스마다 시작점 좌표와 함께 지도 셀 이빅트를 1건씩 예약한다 [R2]")
    void deleteRunnings_schedulesMapCellEvictionPerDistinctCourse() {
        // given : 같은 코스의 러닝 2건 + 다른 코스의 러닝 1건 → 이빅트는 코스당 1건씩 총 2건
        List<Long> ids = List.of(1L, 2L, 3L);

        AtomicBoolean persistenceContextCleared = new AtomicBoolean(false);
        Course courseA = lazyCourse(10L, 37.5665, 126.9780, persistenceContextCleared);
        Course courseB = lazyCourse(20L, 35.1796, 129.0756, persistenceContextCleared);

        givenRunningsOnCourses(ids, courseA, courseA, courseB);

        // 벌크 삭제가 영속성 컨텍스트를 비운다 (clearAutomatically = true)
        doAnswer(invocation -> {
            persistenceContextCleared.set(true);
            return null;
        }).when(runningRepository).deleteInRunningIds(ids);

        // when
        sut.deleteRunnings(ids, memberUuid);

        // then
        ArgumentCaptor<Long> courseIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Double> latCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lngCaptor = ArgumentCaptor.forClass(Double.class);
        verify(courseMapCacheEvictor, times(2))
                .evictCellAfterCommit(courseIdCaptor.capture(), latCaptor.capture(), lngCaptor.capture());

        assertThat(zip(courseIdCaptor.getAllValues(), latCaptor.getAllValues(), lngCaptor.getAllValues()))
                .containsExactlyInAnyOrder(
                        tuple(10L, 37.5665, 126.9780),
                        tuple(20L, 35.1796, 129.0756));
    }

    /** 캡터 세 개를 (courseId, lat, lng) 튜플로 맞춰 본다. */
    private List<org.assertj.core.groups.Tuple> zip(List<Long> courseIds, List<Double> lats, List<Double> lngs) {
        List<org.assertj.core.groups.Tuple> tuples = new ArrayList<>();
        for (int i = 0; i < courseIds.size(); i++) {
            tuples.add(tuple(courseIds.get(i), lats.get(i), lngs.get(i)));
        }
        return tuples;
    }

    /** 삭제 대상 러닝들을 준비한다 — 각 러닝은 인자로 준 코스에 순서대로 매달린다 (같은 코스 반복 가능) */
    private void givenRunningsOnCourses(List<Long> runningIds, Course... coursesOfEachRunning) {
        List<Running> runningsToDelete = new ArrayList<>();
        for (Course course : coursesOfEachRunning) {
            Running running = mock(Running.class);
            when(running.getCourse()).thenReturn(course);
            runningsToDelete.add(running);
        }
        when(runningRepository.findByIds(runningIds)).thenReturn(runningsToDelete);
    }

    /**
     * LAZY Course 프록시를 흉내 낸다 — 식별자 게터는 초기화 없이 동작하지만,
     * 영속성 컨텍스트가 비워진 뒤의 초기화(좌표 접근)는 LazyInitializationException 이다.
     */
    private Course lazyCourse(long courseId, double startLat, double startLng,
                              AtomicBoolean persistenceContextCleared) {
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);  // 식별자 게터는 프록시를 초기화하지 않는다
        when(course.getStartCoordinate()).thenAnswer(invocation -> {
            if (persistenceContextCleared.get()) {
                throw new LazyInitializationException(
                        "could not initialize proxy [Course#" + courseId + "] - no Session");
            }
            return Coordinate.of(startLat, startLng);
        });
        return course;
    }
}
