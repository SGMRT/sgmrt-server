package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;

import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

@Transactional
@DisplayName("CourseCacheEventListener 통합 테스트")
class CourseCacheEventListenerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoSpyBean
    private CourseCacheEventListener courseCacheEventListener;

    @MockitoBean
    private CourseCacheRepository courseCacheRepository;

    @DisplayName("RunFinishedEvent가 발생하면 CourseCacheEventListener가 호출된다.")
    @Test
    void courseCacheInvalidate_RunFinishedEvent() {
        // given
        Long courseId = 1L;
        RunFinishedEvent event = new RunFinishedEvent(1L, courseId, "member-uuid", 5.0);
        willDoNothing().given(courseCacheRepository).deleteById(anyLong());

        // when
        eventPublisher.publishEvent(event);

        // then
        verify(courseCacheEventListener, times(1)).handleRunFinishedEvent(event);
    }

    @DisplayName("RunFinishedEvent가 발생하면 캐시 무효화가 수행된다.")
    @Test
    void handleRunFinishedEvent() {
        // given
        Long courseId = 1L;
        RunFinishedEvent event = new RunFinishedEvent(1L, courseId, "member-uuid", 5.0);
        willDoNothing().given(courseCacheRepository).deleteById(courseId);

        // when
        courseCacheEventListener.handleRunFinishedEvent(event);

        // then
        verify(courseCacheRepository, times(1)).deleteById(courseId);
    }

    @DisplayName("RunUpdatedEvent가 발생하면 CourseCacheEventListener가 호출된다.")
    @Test
    void courseCacheInvalidate_RunUpdatedEvent() {
        // given
        Long courseId = 1L;
        RunUpdatedEvent event = new RunUpdatedEvent(1L, courseId, "member-uuid", "name", true);
        willDoNothing().given(courseCacheRepository).deleteById(anyLong());

        // when
        eventPublisher.publishEvent(event);

        // then
        verify(courseCacheEventListener, times(1)).handleRunUpdatedEvent(event);
    }

    @DisplayName("RunUpdatedEvent가 발생하면 캐시 무효화가 수행된다.")
    @Test
    void handleRunUpdatedEvent() {
        // given
        Long courseId = 1L;
        RunUpdatedEvent event = new RunUpdatedEvent(1L, courseId, "member-uuid", "name", true);
        willDoNothing().given(courseCacheRepository).deleteById(courseId);

        // when
        courseCacheEventListener.handleRunUpdatedEvent(event);

        // then
        verify(courseCacheRepository, times(1)).deleteById(courseId);
    }

}