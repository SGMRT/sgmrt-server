package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.domain.running.domain.events.RunUpdatedEvent;

import java.util.List;

/**
 * 완주·러닝 공개 전환 시 지도 결과셋 캐시(course-map)를 이빅트한다. (설계 cache/05 §4, §6-9)
 *
 * <p><b>왜 이빅트인가</b> — "완주 직후 지도 카드에서 내 등수를 바로 본다"는 요구사항 때문에 TTL 만으로는
 * 부족하다(성장 시뮬레이션 v4: TTL 60초에서도 완주 확인의 14~29%가 미반영 카드를 받음 — cache/06 §6.6).
 * 이빅트가 있어야 TTL 을 늘려(60s→600s) 히트율을 확보하면서도 스테일 0%를 보장할 수 있다.</p>
 *
 * <p><b>왜 Writer 가 아니라 AFTER_COMMIT 리스너인가</b> — "같은 트랜잭션 동기 로직 = 직접 호출,
 * 커밋 후 부수효과 = 이벤트" 규칙(core/04 §6). Writer 는 계속 캐시를 모르고, 커밋 전에 지우면
 * 지운 자리에 다른 요청이 커밋 전 데이터를 재적재하는 레이스가 생기므로 커밋 후에 지운다.</p>
 *
 * <p><b>이빅트 대상 역산</b> — 코스 시작점 ±2km 박스 안에 대표좌표가 있는 모든 region.
 * 캐시 값이 "region 대표좌표 기준 2km bbox 결과"이므로, 이 역산이 곧 "이 코스가 보이는 모든 캐시 엔트리"다.
 * regionId 키 전환으로 코스→키 역산이 가능해졌기에 성립한다 (반올림 키는 불가능했음).</p>
 *
 * <p>비공개 코스(리드모델 부재)는 지도에 없으므로 조용히 스킵. 이빅트 실패는 정합성 사고가 아니라
 * 최대 TTL 만큼의 지연으로 강등되므로 예외를 밖으로 던지지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseMapCacheEvictListener {

    private final CourseReadModelRepository readModelRepository;
    private final RegionRepository regionRepository;
    private final CacheManager cacheManager;

    @TransactionalEventListener
    public void handleRunFinishedEvent(RunFinishedEvent event) {
        evictMapCacheAroundCourse(event.courseId());
    }

    @TransactionalEventListener
    public void handleRunUpdatedEvent(RunUpdatedEvent event) {
        evictMapCacheAroundCourse(event.courseId());
    }

    private void evictMapCacheAroundCourse(Long courseId) {
        try {
            CourseReadModel readModel = readModelRepository.findByCourseId(courseId).orElse(null);
            if (readModel == null) {
                return;
            }
            List<Region> regions = findRegionsCoveringCourse(readModel.getStartLat(), readModel.getStartLng());
            Cache cache = cacheManager.getCache(CourseReadModelReader.COURSE_MAP_CACHE);
            if (cache == null || regions.isEmpty()) {
                return;
            }
            regions.forEach(region -> cache.evict(region.getId()));
            log.info("CourseMapCacheEvictListener - evicted {} region entries around course {}",
                    regions.size(), courseId);
        } catch (Exception e) {
            log.warn("CourseMapCacheEvictListener - evict failed for course {} (stale up to TTL)", courseId, e);
        }
    }

    /**
     * 이빅트 역산 박스의 여유 마진 — 조회 박스(대표좌표 앵커)와 역산 박스(코스 좌표 앵커)는 경도 반폭을
     * 서로 다른 위도의 cos 로 계산해 미세하게 어긋날 수 있다. 역산이 조회의 상위집합(superset)이 되도록
     * 마진을 더해, 경계에 걸친 코스의 캐시가 이빅트에서 누락되는 일이 없게 한다.
     */
    private static final int EVICT_BOX_MARGIN_M = 100;

    /** 코스가 값에 포함되는 캐시 엔트리의 역산 — 대표좌표가 코스 시작점 ±(조회 반경+마진) 박스 안인 region 전부 */
    private List<Region> findRegionsCoveringCourse(Double courseLat, Double courseLng) {
        CourseService.LatLngs box = CourseService.getBoundingBoxLatLngs(
                courseLat, courseLng, CourseReadModelReader.REGION_MAP_RADIUS_M + EVICT_BOX_MARGIN_M);
        return regionRepository.findByCenterLatBetweenAndCenterLngBetween(
                box.minLat(), box.maxLat(), box.minLng(), box.maxLng());
    }
}
