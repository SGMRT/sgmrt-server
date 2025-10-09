package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseCacheEventListenerTest {

    @InjectMocks
    private CourseCacheEventListener courseCacheEventListener;

    @Mock
    private CourseCacheRepository courseCacheRepository;


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