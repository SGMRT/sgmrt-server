package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.dao.CourseCacheRepository;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCacheEventListener {

    private final CourseCacheRepository courseCacheRepository;

    @TransactionalEventListener
    public void handleRunFinishedEvent(RunFinishedEvent event) {
        // 캐시 무효화
        log.info("RunFinishedEvent received; invalidating cache with courseId {}", event.courseId());
        Long courseId = event.courseId();
        courseCacheRepository.deleteById(courseId);
    }

    @TransactionalEventListener
    public void handleRunUpdatedEvent(RunUpdatedEvent event) {
        // 캐시 무효화
        log.info("RunUpdatedEvent received; invalidating cache with courseId {}", event.courseId());
        Long courseId = event.courseId();
        courseCacheRepository.deleteById(courseId);
    }

}
