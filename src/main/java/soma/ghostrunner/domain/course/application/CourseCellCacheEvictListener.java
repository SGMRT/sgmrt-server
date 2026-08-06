package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.dao.CourseCellCache;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics.EvictionResult;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.domain.events.CourseMapDataChangedEvent;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;

/**
 * 지도 데이터가 바뀐 코스가 속한 셀 하나를 이빅트한다. (설계: docs/design/course-cell-bucket-cache-design.md §3-9)
 *
 * <p><b>왜 이빅트인가</b> — "완주 직후 지도에서 내 등수를 바로 본다"는 요구사항 때문에 TTL만으로는 부족하다.
 * 이빅트가 있어야 TTL을 길게 잡아 히트율을 확보하면서도 완주 반영을 즉시 보장할 수 있다.</p>
 *
 * <p><b>왜 AFTER_COMMIT인가</b>({@link TransactionalEventListener} 기본 페이즈) — 커밋 전에 DEL하면 지운 자리에
 * 다른 요청이 <b>커밋 전 데이터</b>를 재적재해 TTL까지 잔존한다(자가 치유가 없다). 커밋 후 DEL은 "이미 반영된 값을
 * 한 번 더 지우는" 안전한 방향으로만 틀린다. 리드모델 조회는 리소스가 언바인드되기 전에 호출되는
 * {@code afterCommit} 동기화에서 이뤄지므로 바인드된 {@code EntityManager}로 그대로 나간다.</p>
 *
 * <p><b>지우는 것은 셀 하나뿐</b> — 코스의 복사본이 캐시에 하나뿐이라 "저장된 곳 = 지울 곳"이 1:1이다.
 * 팬아웃 DEL이 없다는 것이 셀 버킷 전환의 근거이므로 이웃 셀은 건드리지 않는다.</p>
 *
 * <p><b>[R3] 세 핸들러 전부를 try/catch로 감싼다</b> — {@code AbstractPlatformTransactionManager.triggerAfterCommit}은
 * AFTER_COMMIT 동기화에서 던져진 예외를 <b>호출자에게 전파</b>한다. 즉 커밋은 이미 성공했는데 사용자에게는 500이
 * 나가는, 가장 나쁜 형태의 실패가 된다. 이빅트 실패의 손해는 정합성 사고가 아니라 최대 TTL만큼의 스테일이므로
 * 밖으로 던질 이유가 없다. {@link CourseCellCache#evict}가 Redis 예외를 이미 흡수하므로 이 catch가 잡을 것은
 * 좌표 null 같은 프로그래밍 오류뿐이지만, 기존 리스너의 관례를 그대로 유지한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCellCacheEvictListener {

    private final CourseReadModelRepository readModelRepository;
    private final CourseCellCache cellCache;
    private final CourseCellCacheMetrics metrics;

    /**
     * 좌표를 실은 이벤트라 DB 조회가 0회다.
     *
     * <p>코스 삭제는 커밋 후 리드모델이 남아 있지 않아 {@code courseId}로 셀을 역산할 수 없다(M2).
     * 그래서 이 경로만은 이벤트가 들고 온 좌표에 의존한다. 시작점이 없는 코스는 지울 셀을 정할 수 없으므로 건너뛴다.</p>
     */
    @TransactionalEventListener
    public void handleCourseMapDataChanged(CourseMapDataChangedEvent event) {
        try {
            if (event.startLat() == null || event.startLng() == null) {
                return;
            }
            cellCache.evict(GeoCell.of(event.startLat(), event.startLng()));
        } catch (Exception e) {
            log.warn("CourseCellCacheEvictListener - evict failed for course {} (stale up to TTL)",
                    event.courseId(), e);
        }
    }

    @TransactionalEventListener
    public void handleRunFinished(RunFinishedEvent event) {
        evictByCourseId(event.courseId());
    }

    @TransactionalEventListener
    public void handleRunUpdated(RunUpdatedEvent event) {
        evictByCourseId(event.courseId());
    }

    /**
     * 좌표가 없는 이벤트는 리드모델에서 좌표를 되찾아 셀을 정한다.
     *
     * <p>리드모델이 없으면 비공개 코스 = 애초에 지도에 없는 코스다. 지울 셀이 없는 정상 경로이지 실패가 아니므로
     * 예외 대신 메트릭만 남기고 끝낸다.</p>
     */
    private void evictByCourseId(Long courseId) {
        try {
            if (courseId == null) {
                return;
            }
            CourseReadModel readModel = readModelRepository.findByCourseId(courseId).orElse(null);
            if (readModel == null) {
                metrics.recordEviction(EvictionResult.READ_MODEL_ABSENT);
                return;
            }
            cellCache.evict(GeoCell.of(readModel.getStartLat(), readModel.getStartLng()));
        } catch (Exception e) {
            log.warn("CourseCellCacheEvictListener - evict failed for course {} (stale up to TTL)", courseId, e);
        }
    }
}
