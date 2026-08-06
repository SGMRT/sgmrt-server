package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import soma.ghostrunner.domain.course.dao.CourseCellCache;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics.EvictionResult;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.GeoCell;

/**
 * 지도 데이터가 바뀐 코스가 속한 셀 하나를 <b>커밋 후</b> 이빅트한다.
 * (설계: docs/design/course-cell-bucket-cache-design.md §3-9)
 *
 * <p><b>왜 이빅트인가</b> — "완주 직후 지도에서 내 등수를 바로 본다"는 요구사항 때문에 TTL만으로는 부족하다.
 * 이빅트가 있어야 TTL을 길게 잡아 히트율을 확보하면서도 완주 반영을 즉시 보장할 수 있다.</p>
 *
 * <p><b>왜 커밋 후인가</b> — 커밋 전에 DEL하면 지운 자리에 다른 요청이 <b>커밋 전 데이터</b>를 재적재해 TTL까지
 * 잔존한다(자가 치유가 없다). 커밋 후 DEL은 "이미 반영된 값을 한 번 더 지우는" 안전한 방향으로만 틀린다.
 * 리드모델 조회는 리소스가 언바인드되기 전에 호출되는 {@code afterCommit} 동기화에서 이뤄지므로 바인드된
 * {@code EntityManager}로 그대로 나간다.</p>
 *
 * <p><b>왜 이벤트가 아니라 직접 호출인가</b> — "지도 데이터가 변경됐다"는 도메인 사건이 아니라 <b>캐시 무효화 명령</b>이고,
 * 발행자와 구독자가 1:1이었다. 호출자({@code CourseService}, {@code RunningCommandService})는 이미
 * 코스 애플리케이션 계층 빈들을 직접 주입받아 쓰고 있었으므로 이벤트가 결합을 줄이는 게 아니라 가리고만 있었다.
 * 이벤트를 걷어내되 커밋 후 타이밍은 {@link TransactionSynchronizationManager}로 그대로 가져온다.
 * (같은 방향의 선행 작업: 동기 이벤트 제거 1단계)</p>
 *
 * <p><b>지우는 것은 셀 하나뿐</b> — 코스의 복사본이 캐시에 하나뿐이라 "저장된 곳 = 지울 곳"이 1:1이다.
 * 팬아웃 DEL이 없다는 것이 셀 버킷 전환의 근거이므로 이웃 셀은 건드리지 않는다.</p>
 *
 * <p><b>[R3] 커밋 후 콜백 전체를 try/catch로 감싼다</b> —
 * {@code AbstractPlatformTransactionManager.triggerAfterCommit}은 afterCommit 동기화에서 던져진 예외를
 * <b>호출자에게 전파</b>한다. 즉 커밋은 이미 성공했는데 사용자에게는 500이 나가는, 가장 나쁜 형태의 실패가 된다.
 * 이빅트 실패의 손해는 정합성 사고가 아니라 최대 TTL만큼의 스테일이므로 밖으로 던질 이유가 없다.
 * {@link CourseCellCache#evict}가 Redis 예외를 이미 흡수하므로 이 catch가 잡을 것은 좌표 null 같은
 * 프로그래밍 오류뿐이지만, 방어는 그대로 유지한다.</p>
 *
 * <p><b>[R2] 콜백은 primitive만 캡처한다</b> — 커밋 시점에는 영속성 컨텍스트가 정리되어 있어 클로저에 담긴
 * 엔티티·LAZY 프록시를 만지면 {@code LazyInitializationException}이 난다. 좌표·식별자처럼 값만 캡처한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseMapCacheEvictor {

    private final CourseReadModelRepository readModelRepository;
    private final CourseCellCache cellCache;
    private final CourseCellCacheMetrics metrics;

    /**
     * 좌표를 아는 호출자용 — DB 조회가 0회다.
     *
     * <p>코스 삭제는 커밋 후 리드모델이 남아 있지 않아 {@code courseId}로 셀을 역산할 수 없다(M2).
     * 그래서 이 경로만은 호출자가 넘긴 좌표에 의존한다. 시작점이 없는 코스는 지울 셀을 정할 수 없으므로 건너뛴다.</p>
     */
    public void evictCellAfterCommit(Long courseId, Double startLat, Double startLng) {
        if (startLat == null || startLng == null) {
            return;
        }
        double lat = startLat;   // 콜백에 값만 싣는다 (엔티티·프록시 캡처 금지)
        double lng = startLng;
        afterCommit(courseId, () -> cellCache.evict(GeoCell.of(lat, lng)));
    }

    /**
     * {@code courseId}만 아는 호출자용 — 완주·러닝 수정은 커밋 후에도 리드모델이 남아 있어 좌표를 되찾을 수 있다.
     *
     * <p>리드모델이 없으면 비공개 코스 = 애초에 지도에 없는 코스다. 지울 셀이 없는 정상 경로이지 실패가 아니므로
     * 예외 대신 메트릭만 남기고 끝낸다.</p>
     */
    public void evictCourseCellAfterCommit(Long courseId) {
        if (courseId == null) {
            return;
        }
        afterCommit(courseId, () -> evictByCourseId(courseId));
    }

    private void evictByCourseId(Long courseId) {
        CourseReadModel readModel = readModelRepository.findByCourseId(courseId).orElse(null);
        if (readModel == null) {
            metrics.recordEviction(EvictionResult.READ_MODEL_ABSENT);
            return;
        }
        cellCache.evict(GeoCell.of(readModel.getStartLat(), readModel.getStartLng()));
    }

    /**
     * 이빅트를 커밋 후로 미룬다. 트랜잭션 밖에서 불렸다면(동기화 비활성) 미룰 커밋이 없으므로 즉시 실행한다.
     *
     * <p>[R3] 커밋은 이미 끝났으므로 여기서 던지면 성공한 요청이 500이 된다. 실패는 warn 로그로만 남긴다.</p>
     */
    private void afterCommit(Long courseId, Runnable evict) {
        Runnable guarded = () -> {
            try {
                evict.run();
            } catch (Exception e) {
                log.warn("CourseMapCacheEvictor - evict failed for course {} (stale up to TTL)", courseId, e);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guarded.run();
            }
        });
    }
}
