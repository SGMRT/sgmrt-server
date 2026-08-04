package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.exception.RegionNotFoundException;

import java.util.List;

/**
 * 지도(주변 코스) 조회 유즈케이스 — 리드모델 읽기 진입점이자 캐시 지점.
 *
 * <p>캐시 설계 (docs/refactoring/course-read-model/cache/05-cache-key-design.md §4, §6-5)</p>
 * <ul>
 *   <li><b>키 = regionId</b> — 캐시 단위는 좌표 격자가 아니라 행정구역(동네)이다.
 *       키 {@code course-map::{regionId}}, TTL 60초.</li>
 *   <li><b>값의 결정성</b> — 값은 요청자 좌표·반경이 아니라 region <b>대표좌표 + 고정 반경 2km</b>로 정한다.
 *       요청자 값을 쓰면 같은 키에 사용자마다 다른 결과가 적재되어 캐시의 정합성이 깨진다.</li>
 *   <li><b>무효화 없음</b> — 쓰기 경로({@link CourseReadModelWriter})는 캐시를 모른다.
 *       데이터 변경 후 최대 TTL(60초)의 스테일을 허용하며, 리드모델(DB)은 항상 즉시 정정되므로
 *       오류는 일시적이고 자가 치유된다.</li>
 *   <li><b>폴백 경로는 비캐시</b> — regionId 미첨부(팬/줌·구버전 앱)·비기본 요청은 요청 좌표로 직접 조회한다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CourseReadModelReader {

    public static final String COURSE_MAP_CACHE = "course-map";

    /** 랜덤 선별의 모집단 확보를 위해 응답 개수(10)보다 넉넉히 조회한다. */
    private static final int MAP_QUERY_LIMIT = 50;

    /**
     * 지역 캐시 경로의 고정 조회 반경.
     *
     * 요청의 radiusM은 뷰포트 유래 연속값(기기마다 다름)이라 값에 반영하면 같은 키에 다른 결과가 실린다.
     * regionId가 첨부되는 요청은 홈 기본 뷰(≈2km)뿐이므로 서버가 반경을 고정해 결정성을 확보한다 (설계 §4).
     */
    private static final int REGION_MAP_RADIUS_M = 2000;

    private final CourseReadModelRepository readModelRepository;
    private final RegionRepository regionRepository;

    /**
     * 지역(regionId) 캐시 경로 — region 대표좌표 기준 고정 반경 2km 결과셋.
     *
     * <p>대표좌표 조회를 캐시 메서드 <b>안</b>에 두는 것이 핵심이다 — 히트 시 본문이 실행되지 않으므로
     * region 조회를 포함해 DB 접근이 0회가 된다.</p>
     */
    @Cacheable(cacheNames = COURSE_MAP_CACHE, key = "#regionId")
    public List<CourseMapDto> findCoursesForMapByRegion(Long regionId) {
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new RegionNotFoundException(regionId));
        return queryCoursesForMap(region.getCenterLat(), region.getCenterLng(), REGION_MAP_RADIUS_M);
    }

    /** 비캐시 폴백 경로 — 팬/줌, regionId 미첨부, 비기본 요청. 요청 좌표를 그대로 쓴다. */
    public List<CourseMapDto> findCoursesForMap(double lat, double lng, int radiusM) {
        return queryCoursesForMap(lat, lng, radiusM);
    }

    private List<CourseMapDto> queryCoursesForMap(double lat, double lng, int radiusM) {
        CourseService.LatLngs bounds = CourseService.getBoundingBoxLatLngs(lat, lng, radiusM);
        return readModelRepository.findCoursesForMap(
                bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng(), MAP_QUERY_LIMIT);
    }
}
