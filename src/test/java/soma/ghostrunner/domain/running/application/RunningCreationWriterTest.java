package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import soma.ghostrunner.domain.course.application.CourseMapCacheEvictor;
import soma.ghostrunner.domain.course.application.CourseReadModelWriter;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.application.CourseSubscriptionService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.RunningDataUrlsDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 저장 트랜잭션 경계({@link RunningCreationWriter})가 <b>무엇을 안에 품는지</b>를 고정한다.
 *
 * <p>여기서 검증하는 호출들은 전부 "트랜잭션 밖으로 나가면 조용히 망가지는" 것들이다 —
 * MANDATORY 인 리드모델 갱신, 커밋 후 실행을 예약하는 지도 이빅트, AFTER_COMMIT 소비자를 가진 완주 이벤트,
 * 자체 트랜잭션이 없는 구독 생성. 트랜잭션이 실제로 걸리는지 자체는
 * {@code RunningCreationTransactionIntegrationTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RunningCreationWriter 단위 테스트 - 저장 트랜잭션이 품는 부수효과")
class RunningCreationWriterTest {

    @Mock RunningApplicationMapper mapper;
    @Mock RunningRepository runningRepository;
    @Mock CourseService courseService;
    @Mock CourseReadModelWriter courseReadModelWriter;
    @Mock CourseSubscriptionService courseSubscriptionService;
    @Mock CourseMapCacheEvictor courseMapCacheEvictor;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks RunningCreationWriter sut;

    private final CreateRunCommand command = mock(CreateRunCommand.class);
    private final TelemetryStatistics stats = mock(TelemetryStatistics.class);
    private final RunningDataUrlsDto urls = mock(RunningDataUrlsDto.class);

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
        RunningCreationWriter.CreatedRun created = sut.saveRunAndCourse(command, member, stats, urls);

        // then
        assertThat(created.running()).isSameAs(running);
        assertThat(created.course()).isSameAs(course);

        // applyRun 은 ID가 채워진 영속 러닝을 요구한다 — 반드시 save 뒤여야 한다
        var inOrder = inOrder(courseService, runningRepository, courseReadModelWriter);
        inOrder.verify(courseService).save(course);
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
        when(courseService.findCourseByIdFetchJoinMember(courseId)).thenReturn(course);

        Running running = mock(Running.class);
        CourseRunEvent event = new CourseRunEvent(courseId, "한강 코스", 9L, 100L, 0L, 1800L, memberId, "러너");
        when(running.createCourseRunEvent()).thenReturn(event);
        when(mapper.toRunning(command, stats, urls, member, course)).thenReturn(running);
        when(runningRepository.save(running)).thenReturn(running);

        // when
        Running saved = sut.saveRun(command, member, courseId, stats, urls);

        // then
        assertThat(saved).isSameAs(running);
        verify(courseService).findCourseByIdFetchJoinMember(courseId);
        verify(courseReadModelWriter, times(1)).applyRun(running);
        verify(courseSubscriptionService, times(1)).subscribeIfAbsent(courseId, memberId);
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
        when(courseService.findCourseByIdFetchJoinMember(courseId)).thenReturn(course);

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
}
